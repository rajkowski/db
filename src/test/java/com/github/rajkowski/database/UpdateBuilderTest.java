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
import java.sql.SQLException;
import java.util.Arrays;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import junit.framework.TestCase;

public class UpdateBuilderTest extends TestCase {

    private static String uniqueDbName(String baseName) {
        return baseName + "_" + System.nanoTime();
    }

    private static void createUsersTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
            statement.executeUpdate();
        }
    }

    public void testUpdateBuilderBuildsSqlAndParameters() {
        QuerySpec spec = DB.UPDATE("users")
                .SET(new Field("name", "alice"), new Field("active", false))
                .WHERE("id = ?", 10L);

        assertEquals("UPDATE users SET name = ?, active = ? WHERE id = ?", spec.getSql());
        assertEquals(Arrays.asList("alice", false, 10L), spec.getParameters());
    }

    public void testUpdateBuilderCanBeConstructedOutOfOrder() {
        QuerySpec spec = DB.UPDATE("users")
                .WHERE("id = ?", 10L)
                .SET(new Field("name", "alice"));

        assertEquals("UPDATE users SET name = ? WHERE id = ?", spec.getSql());
        assertEquals(Arrays.asList("alice", 10L), spec.getParameters());
    }

    public void testUpdateBuilderSupportsAddStyleAssignments() {
        QuerySpec spec = DB.UPDATE("users")
                .SET()
                .add("name", "alice")
                .add("active", false)
                .WHERE("id = ?", 10L);

        assertEquals("UPDATE users SET name = ?, active = ? WHERE id = ?", spec.getSql());
        assertEquals(Arrays.asList("alice", false, 10L), spec.getParameters());
    }

    public void testUpdateBuilderSupportsDirectSetOverloads() {
        QuerySpec spec = DB.UPDATE("users")
                .SET("name", "alice")
                .add("active", false)
                .WHERE("id = ?", 10L);

        assertEquals("UPDATE users SET name = ?, active = ? WHERE id = ?", spec.getSql());
        assertEquals(Arrays.asList("alice", false, 10L), spec.getParameters());
    }

    public void testUpdateBuilderSupportsPostgisPoints() {
        QuerySpec spec = DB.UPDATE("locations")
                .POINT("geom", 45.0d, -93.0d)
                .WHERE("location_id = ?", 12L);

        assertEquals("UPDATE locations SET geom = ST_SetSRID(ST_MakePoint(45.0, -93.0), 4326) WHERE location_id = ?", spec.getSql());
        assertEquals(Arrays.asList(12L), spec.getParameters());
    }

    public void testUpdateBuilderSupportsJsonbCastAssignments() {
        QuerySpec spec = DB.UPDATE("web_pages")
                .SET("tags", "[\"home\",\"news\"]", CastType.JSONB)
                .WHERE("web_page_id = ?", 12L);

        assertEquals("UPDATE web_pages SET tags = ? WHERE web_page_id = ?", spec.getSql());
        assertEquals(2, spec.getParameters().size());
        assertTrue(spec.getParameters().get(0) instanceof org.postgresql.util.PGobject);
        assertEquals("[\"home\",\"news\"]", ((org.postgresql.util.PGobject) spec.getParameters().get(0)).getValue());
        assertEquals(12L, spec.getParameters().get(1));
    }

    public void testUpdateBuilderSupportsIntervalCastAssignments() {
        QuerySpec spec = DB.UPDATE("jobs")
                .SET("queue_interval", "PT5M", CastType.INTERVAL)
                .WHERE("job_id = ?", 12L);

        assertEquals("UPDATE jobs SET queue_interval = ? WHERE job_id = ?", spec.getSql());
        assertTrue(spec.getParameters().get(0) instanceof org.postgresql.util.PGInterval);
        assertEquals("5 mins", spec.getParameters().get(0).toString());
        assertEquals(12L, spec.getParameters().get(1));
    }

    public void testUpdateBuilderSupportsFluentAndClauses() {
        QuerySpec spec = DB.UPDATE("users")
                .SET("name", "alice")
                .WHERE("id = ?", 10L)
                .AND("active = ?", true);

        assertEquals("UPDATE users SET name = ? WHERE id = ? AND active = ?", spec.getSql());
        assertEquals(Arrays.asList("alice", 10L, true), spec.getParameters());
    }

    public void testUpdateBuilderAllowsRawArithmeticSetExpressions() {
        QuerySpec spec = DB.UPDATE("users")
                .SET("file_count = file_count + " + 3)
                .WHERE("sub_folder_id IN (SELECT sub_folder_id FROM files WHERE file_id = ?)", 42L);

        assertEquals("UPDATE users SET file_count = file_count + 3 WHERE sub_folder_id IN (SELECT sub_folder_id FROM files WHERE file_id = ?)", spec.getSql());
        assertEquals(Arrays.asList(42L), spec.getParameters());
    }

    public void testUpdateBuilderExecutesWithConcreteReturnType() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_update_execute") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);
            createUsersTable(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users (id, name) VALUES (?, ?)")) {
                statement.setInt(1, 10);
                statement.setString(2, "bob");
                statement.executeUpdate();
            }

            boolean updated = DB.UPDATE("users")
                    .SET("name", "alice")
                    .WHERE("id = ?", 10L)
                    .execute();

            assertTrue(updated);
        }
    }

    public void testUpdateBuilderTracksRowsAffected() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_update_track_rows") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);
            createUsersTable(dataSource);

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users (id, name) VALUES (?, ?)")) {
                statement.setInt(1, 10);
                statement.setString(2, "bob");
                statement.executeUpdate();
            }

            Update spec = DB.UPDATE("users")
                    .SET("name", "alice")
                    .WHERE("id = ?", 10L);

            boolean updated = spec.execute();

            assertTrue(updated);
            assertEquals(1, spec.getRowsAffected());
            assertEquals(1, spec.getMetrics().getRowsAffected());
        }
    }
}
