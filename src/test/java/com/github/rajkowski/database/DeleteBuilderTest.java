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

public class DeleteBuilderTest extends TestCase {

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

    public void testDeleteBuilderBuildsSqlAndParameters() {
        QuerySpec spec = DB.DELETE()
                .FROM("users")
                .WHERE("id = ?", 42L);

        assertEquals("DELETE FROM users WHERE id = ?", spec.getSql());
        assertEquals(Arrays.asList(42L), spec.getParameters());
    }

    public void testDeleteBuilderAddsAndClauseAndParameters() {
        QuerySpec spec = DB.DELETE()
                .FROM("items")
                .WHERE("item_id = ?", 42L)
                .AND("category_id = ?", 7L);

        assertEquals("DELETE FROM items WHERE item_id = ? AND category_id = ?", spec.getSql());
        assertEquals(Arrays.asList(42L, 7L), spec.getParameters());
    }

    public void testDeleteBuilderCanSetTableLater() {
        QuerySpec spec = new Delete()
                .WHERE("id = ?", 42L)
                .FROM("users");

        assertEquals("DELETE FROM users WHERE id = ?", spec.getSql());
        assertEquals(Arrays.asList(42L), spec.getParameters());
    }

    public void testDeleteBuilderExecutesWithConcreteReturnType() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_delete_execute") + ";DB_CLOSE_DELAY=-1");
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
                statement.setInt(1, 42);
                statement.setString(2, "bob");
                statement.executeUpdate();
            }

            boolean deleted = new Delete()
                    .WHERE("id = ?", 42L)
                    .FROM("users")
                    .execute();

            assertTrue(deleted);
        }
    }

    public void testDeleteBuilderTracksRowsAffected() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_delete_track_rows") + ";DB_CLOSE_DELAY=-1");
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
                statement.setInt(1, 42);
                statement.setString(2, "bob");
                statement.executeUpdate();
            }

            Delete spec = new Delete()
                    .WHERE("id = ?", 42L)
                    .FROM("users");

            boolean deleted = spec.execute();

            assertTrue(deleted);
            assertEquals(1, spec.getRowsAffected());
            assertEquals(1, spec.getMetrics().getRowsAffected());
        }
    }
}
