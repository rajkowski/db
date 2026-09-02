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
import java.util.Set;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import junit.framework.TestCase;

public class TransactionAndSafetyTest extends TestCase {

    private static String uniqueDbName(String baseName) {
        return baseName + "_" + System.nanoTime();
    }

    private static HikariDataSource createDataSource(String baseName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName(baseName) + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    public void testTenantRegistryFacadeRegistersAndListsTenantDataSources() {
        HikariDataSource primary = createDataSource("db_tenant_registry_facade_primary");
        HikariDataSource replacement = createDataSource("db_tenant_registry_facade_replacement");

        try {
            DB.setTenantRegistry(new TenantRegistry());
            assertTrue(DB.getTenantIds().isEmpty());

            DB.registerTenantDataSource("tenant-a", primary);
            DB.registerTenantDataSource("tenant-b", replacement);

            Set<String> tenantIds = DB.getTenantIds();
            assertEquals(2, tenantIds.size());
            assertTrue(tenantIds.contains("tenant-a"));
            assertTrue(tenantIds.contains("tenant-b"));
            try {
                tenantIds.add("tenant-c");
                fail("Tenant ID snapshot must be immutable");
            } catch (UnsupportedOperationException expected) {
                // Expected: callers cannot mutate registry state through the returned collection.
            }

            DB.registerTenantDataSource("tenant-a", replacement);
            assertSame(replacement, DB.getTenantRegistry().getDataSource("tenant-a"));

            try {
                DB.registerTenantDataSource("", primary);
                fail("Expected IllegalArgumentException for blank tenant id");
            } catch (IllegalArgumentException expected) {
                assertEquals("Tenant id cannot be null or blank", expected.getMessage());
            }
            try {
                DB.registerTenantDataSource("tenant-c", null);
                fail("Expected IllegalArgumentException for null datasource");
            } catch (IllegalArgumentException expected) {
                assertEquals("DataSource cannot be null", expected.getMessage());
            }
        } finally {
            primary.close();
            replacement.close();
        }
    }

    public void testRejectsUnsafeTableIdentifier() {
        try {
            DB.SELECT("id").FROM("users;DROP TABLE users");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Invalid SQL identifier"));
        }
    }

    public void testAllowsSafeSubqueryInWhereClauseWithoutDirectParameter() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_subquery_where") + ";DB_CLOSE_DELAY=-1");
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

            DB.executeUpdate(DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "tenant-user")));

            QuerySpec deleteSpec = DB.DELETE()
                    .FROM("users")
                    .WHERE("id IN (SELECT id FROM users WHERE name = 'tenant-user')");
            assertEquals(1, DB.executeUpdate(deleteSpec));
            assertNull(DB.executeQuery(DB.SELECT("name").FROM("users").WHERE("id = ?", 1)));
        }
    }

    public void testAllowsExistsSubqueryWithSelectOneAndBoundParameter() {
        QuerySpec spec = DB.SELECT("id")
                .FROM("users")
                .WHERE("EXISTS (SELECT 1 FROM user_roles WHERE role_id = lookup_role.role_id AND user_id = ?)", 42);

        assertEquals(
                "SELECT id FROM users WHERE EXISTS (SELECT 1 FROM user_roles WHERE role_id = lookup_role.role_id AND user_id = ?)",
                spec.getSql());
        assertEquals(1, spec.getParameters().size());
        assertEquals(42, spec.getParameters().get(0));
    }

    public void testAllowsBooleanSqlLiterals() {
        QuerySpec spec = DB.SELECT("id").FROM("users").WHERE("active = true");

        assertEquals("SELECT id FROM users WHERE active = true", spec.getSql());
    }

    public void testRejectsNumericLiteralSqlValues() {
        try {
            DB.SELECT("id").FROM("users").WHERE("id = 1");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("parameterized"));
        }
    }

    public void testAllowsNestedExistsWithBooleanConditionsAndBoundParameters() {
        String clause = "(collections.allows_guests = true OR (has_allowed_groups = true AND "
                + "EXISTS (SELECT 1 FROM collection_groups WHERE collection_groups.collection_id = "
                + "collections.collection_id AND view_all = true AND EXISTS (SELECT 1 FROM user_groups "
                + "WHERE user_groups.group_id = collection_groups.group_id AND user_id = ?)) OR "
                + "EXISTS (SELECT 1 FROM members WHERE items.item_id = members.item_id AND user_id = ? "
                + "AND approved IS NOT NULL)))";

        QuerySpec spec = DB.SELECT("items.item_id").FROM("items").WHERE(clause, 7, 7);

        assertEquals("SELECT items.item_id FROM items WHERE " + clause, spec.getSql());
        assertEquals(2, spec.getParameters().size());
        assertEquals(7, spec.getParameters().get(0));
        assertEquals(7, spec.getParameters().get(1));
    }

    public void testUsesThreadLocalConnectionWhenPresent() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_thread_local") + ";DB_CLOSE_DELAY=-1");
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

            try (Connection tenantConnection = dataSource.getConnection()) {
                DB.setThreadLocalConnection(tenantConnection);
                try {
                    QuerySpec insertSpec = DB.INSERT()
                            .INTO("users")
                            .FIELDS(new Field("id", 1), new Field("name", "tenant-user"));
                    assertEquals(1, DB.executeUpdate(insertSpec));

                    QuerySpec selectSpec = DB.SELECT("name").FROM("users").WHERE("id = ?", 1);
                    assertEquals("tenant-user", DB.executeQuery(selectSpec));
                } finally {
                    DB.clearThreadLocalConnection();
                }
            }
        }
    }

    public void testUsesThreadLocalDataSourceForTenantScopedQueries() throws Exception {
        HikariConfig primaryConfig = new HikariConfig();
        primaryConfig.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_tenant_primary") + ";DB_CLOSE_DELAY=-1");
        primaryConfig.setDriverClassName("org.h2.Driver");
        primaryConfig.setUsername("sa");
        primaryConfig.setPassword("");
        primaryConfig.setMaximumPoolSize(2);
        primaryConfig.setMinimumIdle(1);

        HikariConfig secondaryConfig = new HikariConfig();
        secondaryConfig.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_tenant_secondary") + ";DB_CLOSE_DELAY=-1");
        secondaryConfig.setDriverClassName("org.h2.Driver");
        secondaryConfig.setUsername("sa");
        secondaryConfig.setPassword("");
        secondaryConfig.setMaximumPoolSize(2);
        secondaryConfig.setMinimumIdle(1);

        try (HikariDataSource primary = new HikariDataSource(primaryConfig);
                HikariDataSource secondary = new HikariDataSource(secondaryConfig)) {
            DB.setDataSource(primary);

            try (Connection connection = primary.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            try (Connection connection = secondary.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            try {
                DB.setTenantDataSource(secondary);
                DB.withTenantDataSource(secondary, () -> {
                    try {
                        QuerySpec insertSpec = DB.INSERT()
                                .INTO("users")
                                .FIELDS(new Field("id", 1), new Field("name", "tenant-b"));
                        assertEquals(1, DB.executeUpdate(insertSpec));

                        QuerySpec selectSpec = DB.SELECT("name").FROM("users").WHERE("id = ?", 1);
                        assertEquals("tenant-b", DB.executeQuery(selectSpec));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } finally {
                DB.clearTenantDataSource();
            }
        }
    }

    public void testTenantRegistryTracksDataSourcesByTenantId() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_tenant_registry") + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            TenantRegistry registry = new TenantRegistry();
            registry.register("tenant-42", dataSource);

            assertSame(dataSource, registry.getDataSource("tenant-42"));
            assertNull(registry.getDataSource("tenant-99"));

            registry.unregister("tenant-42");
            assertNull(registry.getDataSource("tenant-42"));
        }
    }

    public void testTenantRegistryHelperUsesRegisteredDataSource() throws Exception {
        HikariConfig primaryConfig = new HikariConfig();
        primaryConfig.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_tenant_registry_primary") + ";DB_CLOSE_DELAY=-1");
        primaryConfig.setDriverClassName("org.h2.Driver");
        primaryConfig.setUsername("sa");
        primaryConfig.setPassword("");
        primaryConfig.setMaximumPoolSize(2);
        primaryConfig.setMinimumIdle(1);

        HikariConfig secondaryConfig = new HikariConfig();
        secondaryConfig
                .setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_tenant_registry_secondary") + ";DB_CLOSE_DELAY=-1");
        secondaryConfig.setDriverClassName("org.h2.Driver");
        secondaryConfig.setUsername("sa");
        secondaryConfig.setPassword("");
        secondaryConfig.setMaximumPoolSize(2);
        secondaryConfig.setMinimumIdle(1);

        try (HikariDataSource primary = new HikariDataSource(primaryConfig);
                HikariDataSource secondary = new HikariDataSource(secondaryConfig)) {
            DB.setDataSource(primary);

            try (Connection connection = primary.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }
            try (Connection connection = secondary.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            TenantRegistry registry = new TenantRegistry();
            registry.register("tenant-42", secondary);
            DB.setTenantRegistry(registry);

            DB.withTenant("tenant-42", () -> {
                try {
                    QuerySpec insertSpec = DB.INSERT().INTO("users").FIELDS(new Field("id", 1),
                            new Field("name", "tenant-user"));
                    assertEquals(1, DB.executeUpdate(insertSpec));
                    assertSame(secondary, DB.getDataSource());
                    assertEquals("tenant-user", DB.executeQuery(DB.SELECT("name").FROM("users").WHERE("id = ?", 1)));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertSame(primary, DB.getDataSource());
        }
    }

    public void testRequestScopedTenantSelectionUsesRegisteredDataSource() throws Exception {
        HikariConfig primaryConfig = new HikariConfig();
        primaryConfig.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_request_tenant_primary") + ";DB_CLOSE_DELAY=-1");
        primaryConfig.setDriverClassName("org.h2.Driver");
        primaryConfig.setUsername("sa");
        primaryConfig.setPassword("");
        primaryConfig.setMaximumPoolSize(2);
        primaryConfig.setMinimumIdle(1);

        HikariConfig secondaryConfig = new HikariConfig();
        secondaryConfig.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_request_tenant_secondary") + ";DB_CLOSE_DELAY=-1");
        secondaryConfig.setDriverClassName("org.h2.Driver");
        secondaryConfig.setUsername("sa");
        secondaryConfig.setPassword("");
        secondaryConfig.setMaximumPoolSize(2);
        secondaryConfig.setMinimumIdle(1);

        try (HikariDataSource primary = new HikariDataSource(primaryConfig);
                HikariDataSource secondary = new HikariDataSource(secondaryConfig)) {
            DB.setDataSource(primary);

            try (Connection connection = primary.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }
            try (Connection connection = secondary.getConnection();
                    PreparedStatement statement = connection
                            .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            TenantRegistry registry = new TenantRegistry();
            registry.register("tenant-42", secondary);
            DB.setTenantRegistry(registry);

            DB.setTenant("tenant-42");
            try {
                QuerySpec insertSpec = DB.INSERT().INTO("users").FIELDS(new Field("id", 1),
                        new Field("name", "request-tenant-user"));
                assertEquals(1, DB.executeUpdate(insertSpec));
                assertSame(secondary, DB.getDataSource());
                assertEquals("tenant-42", DB.getTenantId());
                assertEquals("request-tenant-user",
                        DB.executeQuery(DB.SELECT("name").FROM("users").WHERE("id = ?", 1)));
            } finally {
                DB.clearTenant();
            }

            assertNull(DB.getTenantDataSource());
            assertNull(DB.getTenantId());
            assertSame(primary, DB.getDataSource());
        }
    }

    public void testWithTransactionRollsBackOnException() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_transaction_rollback") + ";DB_CLOSE_DELAY=-1");
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

            try {
                DB.withTransaction(dataSource, () -> {
                    try {
                        DB.executeUpdate(
                                DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "tx-user")));
                        throw new IllegalStateException("rollback");
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                fail("Expected IllegalStateException");
            } catch (IllegalStateException expected) {
                assertEquals("rollback", expected.getMessage());
            }

            assertNull(DB.executeQuery(DB.SELECT("name").FROM("users").WHERE("id = ?", 1)));
        }
    }

    public void testStartTransactionAndRollbackHelpersCanManageCommitExplicitly() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_transaction_commit") + ";DB_CLOSE_DELAY=-1");
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
                AutoStartTransaction transaction = DB.startTransaction(connection);
                AutoRollback rollback = DB.rollback(connection);
                try {
                    DB.executeUpdate(connection,
                            DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "explicit-user")));
                    rollback.commit();
                    assertEquals("explicit-user",
                            DB.executeQuery(connection, DB.SELECT("name").FROM("users").WHERE("id = ?", 1)));
                } finally {
                    transaction.close();
                    rollback.close();
                }
            }

            assertEquals("explicit-user", DB.executeQuery(DB.SELECT("name").FROM("users").WHERE("id = ?", 1)));
        }
    }
}
