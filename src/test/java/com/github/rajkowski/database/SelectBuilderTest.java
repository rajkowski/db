/*
 * Copyright 2026 Matt Rajkowski (https://github.com/rajkowski)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rajkowski.database;

import java.util.Arrays;

import junit.framework.TestCase;

public class SelectBuilderTest extends TestCase {

    public void testSelectBuilderBuildsSqlAndParameters() {
        QuerySpec spec = DB.SELECT("id", "name")
                .FROM("users")
                .WHERE("active = ?", true)
                .AND("role = ?", "admin");

        assertEquals("SELECT id, name FROM users WHERE active = ? AND role = ?", spec.getSql());
        assertEquals(Arrays.asList(true, "admin"), spec.getParameters());
    }

    public void testSelectSumFunctionBuildsSql() {
        QuerySpec spec = DB.SELECT("SUM(file_length)")
                .FROM("table");

        assertEquals("SELECT SUM(file_length) FROM table", spec.getSql());
        assertEquals(Arrays.asList(), spec.getParameters());
    }

    public void testComplexFileQueryUsesFluentConditionalClauses() {
        Select builder = DB.SELECT(
                "files.file_id",
                "files.folder_id",
                "files.filename",
                "files.title",
                "files.file_type")
                .FROM("files");

        String filename = " Report.PDF ";
        String[] fileTypes = { "pdf", "txt" };

        if (filename != null) {
            builder.WHERE("LOWER(files.filename) = ?", filename.trim().toLowerCase());
        } else {
            builder.WHERE("files.file_id = ?", 42L);
        }

        builder.AND("LOWER(files.file_type) = ANY(?)", fileTypes);
        builder.AND("LOWER(files.title) LIKE LOWER(?)", "%invoice%");
        builder.ORDER_BY("files.file_id DESC");

        QuerySpec spec = builder;

        assertEquals(
                "SELECT files.file_id, files.folder_id, files.filename, files.title, files.file_type " +
                        "FROM files " +
                        "WHERE LOWER(files.filename) = ? AND LOWER(files.file_type) = ANY(?) AND LOWER(files.title) LIKE LOWER(?) " +
                        "ORDER BY files.file_id DESC",
                spec.getSql());
        assertEquals("report.pdf", spec.getParameters().get(0));
        assertEquals("pdf", spec.getParameters().get(1));
        assertEquals("txt", spec.getParameters().get(2));
        assertEquals("%invoice%", spec.getParameters().get(3));
    }

    public void testSelectBuilderSupportsAnyConditionWithStringArrayCastType() {
        String[] extensions = Arrays.stream(new String[] { "PDF", "JPG" })
                .map(String::toLowerCase)
                .toArray(String[]::new);

        QuerySpec spec = DB.SELECT("id")
                .FROM("files")
                .WHERE("LOWER(files.filename) = ?", "report.pdf")
                .AND("LOWER(files.extension) = ANY(?)", extensions, CastType.ARRAY);

        assertEquals(
                "SELECT id FROM files WHERE LOWER(files.filename) = ? AND LOWER(files.extension) = ANY(?)",
                spec.getSql());
        assertEquals("report.pdf", spec.getParameters().get(0));
        assertSame(extensions, spec.getParameters().get(1));
    }

    public void testSelectBuilderCanBeBuiltOutOfOrderAndColumnsCanBeAddedLater() {
        Select builder = DB.SELECT();
        builder.FROM("users");
        builder.ORDER_BY("name DESC");
        builder.WHERE("active = ?", true);
        builder.SELECT("id", "name");

        QuerySpec spec = builder;

        assertEquals("SELECT id, name FROM users WHERE active = ? ORDER BY name DESC", spec.getSql());
        assertEquals(Arrays.asList(true), spec.getParameters());
    }

    public void testSelectBuilderSupportsJoinsAndCountAggregates() {
        QuerySpec spec = DB.SELECT()
                .COUNT("*")
                .FROM("x")
                .JOIN("table y").ON("x.id = y.id")
                .JOIN("table z").ON("x.id = z.id")
                .WHERE("something = ?", "value")
                .AND("another = ?", 42);

        assertEquals(
                "SELECT COUNT(*) FROM x JOIN table y ON x.id = y.id JOIN table z ON x.id = z.id WHERE something = ? AND another = ?",
                spec.getSql());
        assertEquals(Arrays.asList("value", 42), spec.getParameters());
    }

        public void testSelectBuilderSupportsLeftJoins() {
                QuerySpec spec = DB.SELECT("users.id", "profiles.display_name")
                                .FROM("users")
                                .LEFT_JOIN("profiles").ON("users.id = profiles.user_id")
                                .WHERE("users.active = ?", true);

                assertEquals(
                                "SELECT users.id, profiles.display_name FROM users LEFT JOIN profiles ON users.id = profiles.user_id WHERE users.active = ?",
                                spec.getSql());
                assertEquals(Arrays.asList(true), spec.getParameters());
        }

    public void testSelectBuilderSupportsDerivedTableFromUnionSubquery() {
        Select subquery = DB.SELECT("jsonb_array_elements_text(tags) AS tag")
                .FROM("web_pages")
                .WHERE("tags IS NOT NULL")
                .AND("enabled = ?", true)
                .AND("draft = ?", false)
                .UNION_ALL(DB.SELECT("jsonb_array_elements_text(tags) AS tag")
                        .FROM("items")
                        .WHERE("tags IS NOT NULL"));

        QuerySpec spec = DB.SELECT("DISTINCT tag")
                .FROM(subquery, "all_tags")
                .WHERE("tag IS NOT NULL")
                .AND("tag <> ?", "")
                .ORDER_BY("tag");

        assertEquals(
                "SELECT DISTINCT tag FROM (SELECT jsonb_array_elements_text(tags) AS tag FROM web_pages WHERE tags IS NOT NULL AND enabled = ? AND draft = ? UNION ALL SELECT jsonb_array_elements_text(tags) AS tag FROM items WHERE tags IS NOT NULL) AS all_tags WHERE tag IS NOT NULL AND tag <> ? ORDER BY tag",
                spec.getSql());
        assertEquals(Arrays.asList(true, false, ""), spec.getParameters());
    }

    public void testSelectBuilderSupportsAliasValueObjectAndFluentAsMethod() {
        Select subquery = DB.SELECT("id").FROM("users");

        QuerySpec subquerySpec = DB.SELECT("*")
                .FROM(subquery, DB.AS("all_users"))
                .WHERE("id > ?", 0);

        assertEquals("SELECT * FROM (SELECT id FROM users) AS all_users WHERE id > ?", subquerySpec.getSql());
        assertEquals(Arrays.asList(0), subquerySpec.getParameters());

        QuerySpec aliasedTableSpec = DB.SELECT("*")
                .FROM("users")
                .AS("u");

        assertEquals("SELECT * FROM users AS u", aliasedTableSpec.getSql());
    }

    public void testSelectBuilderSkipsSentinelValuesForConditionalWhereAndClauses() {
        QuerySpec spec = DB.SELECT("id")
                .FROM("users")
                .WHERE("active = ?", true)
                .AND_SKIP_IF_MATCHES("folder_id = ?", -1L, -1L)
                .AND_SKIP_IF_MATCHES("role = ?", null, null)
                .AND_SKIP_IF_MATCHES("name = ?", "alice", "skip-me");

        assertEquals("SELECT id FROM users WHERE active = ? AND name = ?", spec.getSql());
        assertEquals(Arrays.asList(true, "alice"), spec.getParameters());

        QuerySpec spec2 = DB.SELECT("id")
                .FROM("users")
                .WHERE_SKIP_IF_MATCHES("active = ?", false, false)
                .AND_SKIP_IF_MATCHES("tenant_id = ?", 99L, 0L)
                .AND_SKIP_IF_MATCHES("status = ?", "active", "skip-me");

        assertEquals("SELECT id FROM users WHERE tenant_id = ? AND status = ?", spec2.getSql());
        assertEquals(Arrays.asList(99L, "active"), spec2.getParameters());
    }

    public void testStaticWhereFactoryAndParameterizedSelectExpressionsAreSupported() {
        QuerySpec spec = DB.SELECT().WHERE("active = ?", true)
                .FROM("users")
                .SELECT("id", "name")
                .ORDER_BY("name DESC");

        assertEquals("SELECT id, name FROM users WHERE active = ? ORDER BY name DESC", spec.getSql());
        assertEquals(Arrays.asList(true), spec.getParameters());
    }

    public void testJsonbTagConditionOperatorConstantsAreDefined() {
        assertEquals("AND", ConditionGroup.ALL);
        assertEquals("OR", ConditionGroup.ANY);
        assertEquals("NOT AND", ConditionGroup.NONE);
        assertEquals("NOT OR", ConditionGroup.NOT_ANY);
    }

    public void testJsonbTagConditionRepeatsEachTagValue() {
        ConditionGroup condition = ConditionGroup.build("web_pages.tags", new String[] { "news", "featured" }, ConditionGroup.ANY);

        assertEquals("(web_pages.tags @> ?::jsonb OR web_pages.tags @> ?::jsonb)", condition.sql());
        assertEquals(Arrays.asList("[\"news\"]", "[\"featured\"]"), Arrays.asList(condition.values()));
    }

    public void testJsonbTagConditionSupportsNotOperator() {
        ConditionGroup condition = ConditionGroup.build("web_pages.tags", new String[] { "news", "featured" }, ConditionGroup.NOT_ANY, CastType.JSONB);

        assertEquals("NOT (web_pages.tags @> ?::jsonb OR web_pages.tags @> ?::jsonb)", condition.sql());
        assertEquals(Arrays.asList("[\"news\"]", "[\"featured\"]"), Arrays.asList(condition.values()));
    }

    public void testSelectBuilderSupportsParameterizedOrderBy() {
        QuerySpec spec = DB.SELECT("id", "geom")
                .FROM("zip_codes")
                .WHERE("active = ?", true)
                .ORDER_BY("geom <-> (SELECT geom FROM zip_codes WHERE code = ?)", "12345");

        assertEquals("SELECT id, geom FROM zip_codes WHERE active = ? ORDER BY geom <-> (SELECT geom FROM zip_codes WHERE code = ?)", spec.getSql());
        assertEquals(Arrays.asList(true, "12345"), spec.getParameters());
    }

    public void testSelectBuilderSupportsParameterizedOrderByWithMultipleValues() {
        QuerySpec spec = DB.SELECT("id", "name")
                .FROM("users")
                .WHERE("status = ?", "active")
                .ORDER_BY("CASE WHEN role = ? THEN ? ELSE ? END DESC", "admin", 1, 2);

        assertEquals("SELECT id, name FROM users WHERE status = ? ORDER BY CASE WHEN role = ? THEN ? ELSE ? END DESC", spec.getSql());
        assertEquals(Arrays.asList("active", "admin", 1, 2), spec.getParameters());
    }

        public void testSelectBuilderOrdersParametersByGeneratedSqlWhenBuiltOutOfOrder() {
                Select builder = DB.SELECT().FROM("users");
                builder.SELECT("COALESCE(?, id) AS first_value", 1)
                                .AND("enabled = ?", 2)
                                .ORDER_BY("CASE WHEN priority = ? THEN id END", 3)
                                .AND("role_id = ?", 4)
                                .SELECT("COALESCE(?, id) AS second_value", 5);

                assertEquals(
                                "SELECT COALESCE(?, id) AS first_value, COALESCE(?, id) AS second_value FROM users " +
                                                "WHERE enabled = ? AND role_id = ? ORDER BY CASE WHEN priority = ? THEN id END",
                                builder.getSql());
                assertEquals(Arrays.asList(1, 5, 2, 4, 3), builder.getParameters());
        }
}
