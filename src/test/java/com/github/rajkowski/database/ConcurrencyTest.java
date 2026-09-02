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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import junit.framework.TestCase;

public class ConcurrencyTest extends TestCase {

    private static String uniqueDbName(String baseName) {
        return baseName + "_" + System.nanoTime();
    }

    private static HikariDataSource createDataSource(String baseName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName(baseName) + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    private static void createUsersTable(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
            statement.executeUpdate();
        }
    }

    public void testThreadLocalScopesRemainIsolatedAcrossThreads() throws Exception {
        HikariDataSource left = createDataSource("db_concurrent_left");
        HikariDataSource right = createDataSource("db_concurrent_right");

        createUsersTable(left);
        createUsersTable(right);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            final int threadIndex = i;
            final DataSource dataSource = threadIndex == 0 ? left : right;
            final String expectedName = threadIndex == 0 ? "left-user" : "right-user";

            tasks.add(() -> {
                try {
                    DB.withDataSource(dataSource, () -> {
                        try {
                            assertSame(dataSource, DB.getDataSource());
                            try (Connection connection = dataSource.getConnection()) {
                                DB.withConnection(connection, () -> {
                                    try {
                                        assertSame(connection, DB.getThreadLocalConnection());
                                        assertSame(dataSource, DB.getDataSource());

                                        DB.executeUpdate(DB.INSERT().INTO("users")
                                                .FIELDS(new Field("id", 1), new Field("name", expectedName)));

                                        assertEquals(expectedName, DB.executeQuery(DB.SELECT("name")
                                                .FROM("users")
                                                .WHERE("id = ?", 1)));
                                    } catch (SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                                assertNull(DB.getThreadLocalConnection());
                            }
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    assertNull(DB.getTenantDataSource());
                    return null;
                } catch (RuntimeException e) {
                    throw e;
                }
            });
        }

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
            left.close();
            right.close();
        }
    }

    public void testTenantContextIsRestoredAcrossNestedScopes() throws Exception {
        HikariDataSource primary = createDataSource("db_concurrent_primary");
        HikariDataSource secondary = createDataSource("db_concurrent_secondary");

        createUsersTable(primary);
        createUsersTable(secondary);

        try {
            DB.setDataSource(primary);
            DB.withDataSource(primary, () -> {
                try {
                    assertSame(primary, DB.getDataSource());

                    DB.withTenantDataSource(secondary, () -> {
                        assertSame(secondary, DB.getDataSource());
                        assertNull(DB.getThreadLocalConnection());

                        final Connection[] scopedConnection = new Connection[1];
                        try {
                            scopedConnection[0] = primary.getConnection();
                            DB.withConnection(scopedConnection[0], () -> {
                                assertSame(scopedConnection[0], DB.getThreadLocalConnection());
                                assertSame(secondary, DB.getDataSource());
                            });
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        } finally {
                            try {
                                if (scopedConnection[0] != null) {
                                    scopedConnection[0].close();
                                }
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        assertSame(secondary, DB.getDataSource());
                        assertNull(DB.getThreadLocalConnection());
                    });

                    assertSame(primary, DB.getDataSource());
                } catch (RuntimeException e) {
                    throw e;
                }
            });

            assertNull(DB.getTenantDataSource());
            assertNull(DB.getThreadLocalConnection());
        } finally {
            primary.close();
            secondary.close();
        }
    }

    public void testTenantRegistryUsesSeparateThreadLocalContexts() throws Exception {
        HikariDataSource primary = createDataSource("db_tenant_primary");
        HikariDataSource secondary = createDataSource("db_tenant_secondary");

        createUsersTable(primary);
        createUsersTable(secondary);

        TenantRegistry registry = new TenantRegistry();
        registry.register("tenant-a", primary);
        registry.register("tenant-b", secondary);
        DB.setTenantRegistry(registry);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            final String tenantId = i == 0 ? "tenant-a" : "tenant-b";
            final DataSource expectedDataSource = i == 0 ? primary : secondary;
            final String expectedName = i == 0 ? "tenant-a-user" : "tenant-b-user";

            tasks.add(() -> {
                try {
                    DB.withTenant(tenantId, () -> {
                        try {
                            assertSame(expectedDataSource, DB.getDataSource());
                            DB.executeUpdate(DB.INSERT().INTO("users")
                                    .FIELDS(new Field("id", 1), new Field("name", expectedName)));
                            assertEquals(expectedName, DB.executeQuery(DB.SELECT("name")
                                    .FROM("users")
                                    .WHERE("id = ?", 1)));
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    assertNull(DB.getTenantDataSource());
                    return null;
                } catch (RuntimeException e) {
                    throw e;
                }
            });
        }

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }

            assertNull(DB.getTenantDataSource());
            assertNull(DB.getThreadLocalConnection());
        } finally {
            executor.shutdownNow();
            primary.close();
            secondary.close();
        }
    }

    public void testTenantRegistryReturnsSnapshotOfRegisteredTenantIds() {
        HikariDataSource primary = createDataSource("db_registry_primary");
        HikariDataSource secondary = createDataSource("db_registry_secondary");

        try {
            TenantRegistry registry = new TenantRegistry();
            registry.register("tenant-a", primary);
            registry.register("tenant-b", secondary);

            Set<String> tenantIds = registry.getTenantIds();

            assertEquals(2, tenantIds.size());
            assertTrue(tenantIds.containsAll(Arrays.asList("tenant-a", "tenant-b")));
            try {
                tenantIds.add("tenant-c");
                fail("Tenant ID snapshot must be immutable");
            } catch (UnsupportedOperationException expected) {
                // Expected: callers cannot mutate registry state through the returned collection.
            }
            registry.unregister("tenant-a");
            assertTrue(tenantIds.contains("tenant-a"));
        } finally {
            primary.close();
            secondary.close();
        }
    }

    public void testTenantContextIsRestoredAfterNestedTenantScope() {
        HikariDataSource primary = createDataSource("db_nested_tenant_primary");
        HikariDataSource secondary = createDataSource("db_nested_tenant_secondary");

        try {
            TenantRegistry registry = new TenantRegistry();
            registry.register("tenant-a", primary);
            registry.register("tenant-b", secondary);
            DB.setTenantRegistry(registry);

            DB.withTenant("tenant-a", () -> {
                assertEquals("tenant-a", DB.getTenantId());
                assertSame(primary, DB.getDataSource());
                DB.withTenant("tenant-b", () -> {
                    assertEquals("tenant-b", DB.getTenantId());
                    assertSame(secondary, DB.getDataSource());
                });
                assertEquals("tenant-a", DB.getTenantId());
                assertSame(primary, DB.getDataSource());
            });

            assertNull(DB.getTenantId());
            assertNull(DB.getTenantDataSource());
        } finally {
            primary.close();
            secondary.close();
        }
    }
}
