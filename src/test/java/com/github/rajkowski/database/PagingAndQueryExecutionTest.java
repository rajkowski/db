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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import junit.framework.TestCase;

public class PagingAndQueryExecutionTest extends TestCase {

    private static String uniqueDbName(String baseName) {
        return baseName + "_" + System.nanoTime();
    }

    public void testBuilderReturnListAndReturnRecordWork() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_return_helpers") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "alice")).execute();
            DB.INSERT().INTO("users").FIELDS(new Field("id", 2), new Field("name", "bob")).execute();

            List<String> names = DB.SELECT("name")
                    .FROM("users")
                    .ORDER_BY("name ASC")
                    .returnList(rs -> rs.getString("name"));
            assertEquals(Arrays.asList("alice", "bob"), names);

            DataResult<String> namesResult = DB.SELECT("name")
                    .FROM("users")
                    .ORDER_BY("name ASC")
                    .returnDataResult(rs -> rs.getString("name"));
            assertEquals(Arrays.asList("alice", "bob"), namesResult.getRecords());
            assertEquals(-1L, namesResult.getTotalRecordCount());

            String first = DB.SELECT("name")
                    .FROM("users")
                    .WHERE("id = ?", 2)
                    .returnRecord(rs -> rs.getString("name"));
            assertEquals("bob", first);
        }
    }

    public void testReturnHelpersDoNotRequireCheckedExceptions() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_return_helper_contract") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "alice")).execute();

            List<String> names = DB.SELECT("name").FROM("users").ORDER_BY("name ASC").returnList(rs -> rs.getString("name"));
            String firstName = DB.SELECT("name").FROM("users").WHERE("id = ?", 1).returnRecord(rs -> rs.getString("name"));
            DataResult<String> result = DB.SELECT("name").FROM("users").ORDER_BY("name ASC").returnDataResult(rs -> rs.getString("name"));

            assertEquals(Collections.singletonList("alice"), names);
            assertEquals("alice", firstName);
            assertEquals(Collections.singletonList("alice"), result.getRecords());
        }
    }

    public void testSelectHelpersReturnDefaultValuesWhenQueryFails() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_select_error_helpers") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            QuerySpec brokenSelect = DB.SELECT("name").FROM("missing_table");

            assertEquals(Collections.emptyList(), brokenSelect.returnList(rs -> rs.getString("name")));
            assertNull(brokenSelect.returnRecord(rs -> rs.getString("name")));
            assertNull(brokenSelect.executeQuery());
            assertEquals(Collections.emptyList(), brokenSelect.executeList());

            DataResult<String> result = brokenSelect.returnDataResult(rs -> rs.getString("name"));
            assertNotNull(result);
            assertEquals(Collections.emptyList(), result.getRecords());
        }
    }

    public void testSelectBuilderSupportsLimitOffset() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_paging_helpers") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "alice")).execute();
            DB.INSERT().INTO("users").FIELDS(new Field("id", 2), new Field("name", "bob")).execute();
            DB.INSERT().INTO("users").FIELDS(new Field("id", 3), new Field("name", "carol")).execute();

            Paging paging = new Paging(2, 2);
            List<String> names = DB.SELECT("name")
                    .FROM("users")
                    .ORDER_BY("id ASC")
                    .LIMIT(2)
                    .OFFSET(2)
                    .PAGING(paging)
                    .returnList(rs -> rs.getString("name"));

            assertEquals(Arrays.asList("carol"), names);
            DataResult<String> namesResult = DB.SELECT("name")
                    .FROM("users")
                    .ORDER_BY("id ASC")
                    .LIMIT(2)
                    .OFFSET(2)
                    .PAGING(paging)
                    .returnDataResult(rs -> rs.getString("name"));

            assertEquals(Arrays.asList("carol"), namesResult.getRecords());
            assertEquals(3L, namesResult.getTotalRecordCount());
            assertEquals(2, paging.getPageSize());
            assertEquals(2, paging.getPageNumber());
            assertEquals(2, paging.getOffset());
            assertEquals(3L, paging.getTotalCount());
        }
    }

    public void testSelectBuilderSupportsPaging() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_paging_helpers") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "alice")).execute();
            DB.INSERT().INTO("users").FIELDS(new Field("id", 2), new Field("name", "bob")).execute();
            DB.INSERT().INTO("users").FIELDS(new Field("id", 3), new Field("name", "carol")).execute();

            Paging paging = new Paging(2, 2);
            List<String> names = DB.SELECT("name")
                    .FROM("users")
                    .ORDER_BY("id ASC")
                    .PAGING(paging)
                    .returnList(rs -> rs.getString("name"));

            assertEquals(Arrays.asList("carol"), names);
            DataResult<String> namesResult = DB.SELECT("name")
                    .FROM("users")
                    .ORDER_BY("id ASC")
                    .PAGING(paging)
                    .returnDataResult(rs -> rs.getString("name"));

            assertEquals(Arrays.asList("carol"), namesResult.getRecords());
            assertEquals(3L, namesResult.getTotalRecordCount());
            assertEquals(2, paging.getPageSize());
            assertEquals(2, paging.getPageNumber());
            assertEquals(2, paging.getOffset());
            assertEquals(3L, paging.getTotalCount());
        }
    }

    public void testSelectBuilderSupportsDataConstraintsForOrderAndPaging() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_constraint_paging") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "alice")).execute();
            DB.INSERT().INTO("users").FIELDS(new Field("id", 2), new Field("name", "bob")).execute();
            DB.INSERT().INTO("users").FIELDS(new Field("id", 3), new Field("name", "carol")).execute();

            DataConstraints constraints = new DataConstraints(2, 2, "id", "ASC");
            List<String> names = DB.SELECT("name")
                    .FROM("users")
                    .ORDER_BY(constraints)
                    .PAGING(constraints)
                    .returnList(rs -> rs.getString("name"));

            assertEquals(Arrays.asList("carol"), names);
            DataResult<String> namesResult = DB.SELECT("name")
                    .FROM("users")
                    .ORDER_BY(constraints)
                    .PAGING(constraints)
                    .returnDataResult(rs -> rs.getString("name"));

            assertEquals(Arrays.asList("carol"), namesResult.getRecords());
            assertEquals(3L, namesResult.getTotalRecordCount());
            assertEquals(2, constraints.getPageSize());
            assertEquals(2, constraints.getPageNumber());
            assertEquals(2, constraints.getOffset());
            assertEquals(3L, constraints.getTotalRecordCount());
        }
    }

    public void testUsesInitializedDataSourceWithoutPassingConnection() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_pool_test") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("INSERT INTO users (id, name) VALUES (?, ?)")) {
                statement.setInt(1, 7);
                statement.setString(2, "alice");
                statement.executeUpdate();
            }

            DB.setDataSource(dataSource);

            QuerySpec selectSpec = DB.SELECT("name").FROM("users").WHERE("id = ?", 7);
            QuerySpec insertSpec = DB.INSERT()
                    .INTO("users")
                    .FIELDS(new Field("id", 9), new Field("name", "bob"));

            assertEquals("alice", DB.executeQuery(selectSpec));
            assertEquals(1, DB.executeUpdate(insertSpec));
        }
    }

    public void testBuilderExecuteWorksWithAndWithoutConnection() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_builder_execute") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            Insert insert = DB.INSERT()
                    .INTO("users")
                    .FIELDS(new Field("id", 1), new Field("name", "charlie"));
            long generatedId = insert.execute();
            assertEquals(1L, generatedId);

            QuerySpec selectSpec = DB.SELECT("name").FROM("users").WHERE("id = ?", 1);
            assertEquals("charlie", selectSpec.execute());

            try (Connection connection = dataSource.getConnection()) {
                Update updateSpec = DB.UPDATE("users")
                        .SET(new Field("name", "dana"))
                        .WHERE("id = ?", 1);
                assertTrue(updateSpec.execute(connection));
                assertEquals(1, updateSpec.getRowsAffected());
            }

            QuerySpec fetchSpec = DB.SELECT("name").FROM("users").WHERE("id = ?", 1);
            assertEquals("dana", fetchSpec.execute());
        }
    }

    public void testQuerySpecTracksExecutionTimeAndStatus() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_query_metrics") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            QuerySpec successfulQuery = DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "alice"));
            assertEquals(1L, successfulQuery.execute());
            assertTrue(successfulQuery.isSuccess());
            assertTrue(successfulQuery.getExecutionTime() >= 0L);
            assertEquals("SUCCESS", successfulQuery.getStatus());

            QuerySpec failedQuery = DB.SELECT("name").FROM("missing_table").WHERE("id = ?", 1);
            assertNull(failedQuery.executeQuery());
            assertFalse(failedQuery.isSuccess());
            assertTrue(failedQuery.getExecutionTime() >= 0L);
            assertNotNull(failedQuery.getStatus());
        }
    }

    public void testQuerySpecAcceptsExplicitMetricsObject() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_explicit_metrics") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            QueryMetrics insertMetrics = new QueryMetrics();
            QuerySpec insertQuery = DB.INSERT().METRICS(insertMetrics).INTO("users")
                    .FIELDS(new Field("id", 1), new Field("name", "alice"));
            assertEquals(1L, insertQuery.execute());
            assertSame(insertMetrics, insertQuery.getMetrics());
            assertEquals("SUCCESS", insertMetrics.getStatus());

            QueryMetrics selectMetrics = new QueryMetrics();
            QuerySpec selectQuery = DB.SELECT("name").METRICS(selectMetrics).FROM("users").WHERE("id = ?", 1);
            assertEquals("alice", selectQuery.executeQuery());
            assertSame(selectMetrics, selectQuery.getMetrics());
            assertEquals("SUCCESS", selectMetrics.getStatus());
        }
    }

    public void testConnectionAndDatasourceScopeHelpersWork() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_scoped_helpers") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            try (Connection connection = dataSource.getConnection()) {
                DB.withConnection(connection, () -> {
                    try {
                        QuerySpec insertSpec = DB.INSERT().INTO("users").FIELDS(new Field("id", 1),
                                new Field("name", "scoped-user"));
                        assertEquals(1, DB.executeUpdate(insertSpec));
                        assertSame(connection, DB.getThreadLocalConnection());
                        assertEquals("scoped-user",
                                DB.executeQuery(DB.SELECT("name").FROM("users").WHERE("id = ?", 1)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            DataSource previous = DB.getDataSource();
            DB.withDataSource(dataSource, () -> {
                try {
                    assertSame(dataSource, DB.getDataSource());
                    assertEquals("scoped-user", DB.executeQuery(DB.SELECT("name").FROM("users").WHERE("id = ?", 1)));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertSame(previous, DB.getDataSource());
        }
    }
}
