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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class TenantConnectionManagerTest {

    @Test
    public void aggregateBudgetBlocksSecondTenantUntilFirstConnectionCloses() throws Exception {
        try (HikariDataSource first = createDataSource("manager_first");
                HikariDataSource second = createDataSource("manager_second")) {
            TenantConnectionManager manager = new TenantConnectionManager();
            manager.setMaximumConnections(1);
            manager.register("first", first);
            manager.register("second", second);

            try (Connection held = manager.getConnection("first")) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                try {
                    Future<Connection> waitingConnection = executor.submit(() -> manager.getConnection("second"));
                    assertFalse(waitingConnection.isDone());
                    held.close();
                    try (Connection released = waitingConnection.get(5, TimeUnit.SECONDS)) {
                        assertNotNull(released);
                    }
                } finally {
                    executor.shutdownNow();
                }
            }
        }
    }

    @Test
    public void leastRecentlyUsedIdlePoolIsEvictedBeforeNewTenantConnection() throws Exception {
        try (HikariDataSource first = createDataSource("manager_lru_first");
                HikariDataSource second = createDataSource("manager_lru_second")) {
            TenantConnectionManager manager = new TenantConnectionManager();
            manager.setMaximumConnections(1);
            manager.register("first", first);
            manager.register("second", second);

            try (Connection ignored = manager.getConnection("first")) {
                assertTrue(first.getHikariPoolMXBean().getTotalConnections() > 0);
            }
            try (Connection ignored = manager.getConnection("second")) {
                assertTrue(second.getHikariPoolMXBean().getTotalConnections() > 0);
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (first.getHikariPoolMXBean().getTotalConnections() > 0 && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertTrue(first.getHikariPoolMXBean().getTotalConnections() == 0);
        }
    }

    @Test
    public void onlyLeastRecentlyUsedIdlePoolIsEvictedWhenGroupIsFull() throws Exception {
        try (HikariDataSource first = createDataSource("manager_lru_only_first");
                HikariDataSource second = createDataSource("manager_lru_only_second");
                HikariDataSource third = createDataSource("manager_lru_only_third")) {
            TenantConnectionManager manager = new TenantConnectionManager();
            manager.setMaximumConnections(2);
            manager.register("first", first);
            manager.register("second", second);
            manager.register("third", third);

            try (Connection ignored = manager.getConnection("first")) {
                assertTrue(first.getHikariPoolMXBean().getTotalConnections() > 0);
            }
            try (Connection ignored = manager.getConnection("second")) {
                assertTrue(second.getHikariPoolMXBean().getTotalConnections() > 0);
            }
            try (Connection ignored = manager.getConnection("third")) {
                assertTrue(third.getHikariPoolMXBean().getTotalConnections() > 0);
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (first.getHikariPoolMXBean().getTotalConnections() > 0 && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertTrue(first.getHikariPoolMXBean().getTotalConnections() == 0);
            assertTrue(second.getHikariPoolMXBean().getTotalConnections() > 0);
        }
    }

    @Test
    public void tenantsInDifferentPoolGroupsAreNotThrottledByEachOther() throws Exception {
        try (HikariDataSource shared = createDataSource("manager_group_shared");
                HikariDataSource dedicated = createDataSource("manager_group_dedicated")) {
            TenantConnectionManager manager = new TenantConnectionManager();
            manager.setMaximumConnections("shared-server", 1);
            manager.register("tenant-a", shared, "shared-server");
            manager.register("tenant-b", dedicated, "dedicated-server");

            try (Connection heldShared = manager.getConnection("tenant-a")) {
                // tenant-b has its own pool group budget, so it must not block on tenant-a's exhausted budget
                try (Connection dedicatedConnection = manager.getConnection("tenant-b")) {
                    assertNotNull(dedicatedConnection);
                }
            }
        }
    }

    @Test
    public void sharedPoolGroupBlocksSecondTenantUntilFirstConnectionCloses() throws Exception {
        try (HikariDataSource first = createDataSource("manager_group_first");
                HikariDataSource second = createDataSource("manager_group_second")) {
            TenantConnectionManager manager = new TenantConnectionManager();
            manager.setMaximumConnections("shared-server", 1);
            manager.register("first", first, "shared-server");
            manager.register("second", second, "shared-server");

            try (Connection held = manager.getConnection("first")) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                try {
                    Future<Connection> waitingConnection = executor.submit(() -> manager.getConnection("second"));
                    assertFalse(waitingConnection.isDone());
                    held.close();
                    try (Connection released = waitingConnection.get(5, TimeUnit.SECONDS)) {
                        assertNotNull(released);
                    }
                } finally {
                    executor.shutdownNow();
                }
            }
        }
    }

    private static HikariDataSource createDataSource(String name) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + name + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000);
        return new HikariDataSource(config);
    }
}