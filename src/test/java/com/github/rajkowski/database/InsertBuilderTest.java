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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import junit.framework.TestCase;

public class InsertBuilderTest extends TestCase {

    public void testInsertBuilderBuildsSqlAndParameters() {
        QuerySpec spec = DB.INSERT()
                .INTO("users")
                .FIELDS(
                        new Field("name", "alice"),
                        new Field("active", true));

        assertEquals("INSERT INTO users (name, active) VALUES (?, ?)", spec.getSql());
        assertEquals(Arrays.asList("alice", true), spec.getParameters());
    }

    public void testInsertBuilderCanBeConstructedOutOfOrder() {
        QuerySpec spec = DB.INSERT()
                .FIELDS(new Field("active", true))
                .INTO("users");

        assertEquals("INSERT INTO users (active) VALUES (?)", spec.getSql());
        assertEquals(Arrays.asList(true), spec.getParameters());
    }

    public void testInsertBuilderSupportsFluentAddMethod() {
        QuerySpec spec = DB.INSERT()
                .INTO("users")
                .FIELDS()
                .FIELD("name", "alice")
                .FIELD("active", true)
                .FIELD("count", 42L)
                .FIELD("balance", 12.5d);

        assertEquals("INSERT INTO users (name, active, count, balance) VALUES (?, ?, ?, ?)", spec.getSql());
        assertEquals(Arrays.asList("alice", true, 42L, 12.5d), spec.getParameters());
    }

    public void testInsertBuilderSupportsDirectFieldOverloads() {
        QuerySpec spec = DB.INSERT()
                .INTO("users")
                .FIELD("name", "alice")
                .FIELD("active", false)
                .FIELD("count", 42L);

        assertEquals("INSERT INTO users (name, active, count) VALUES (?, ?, ?)", spec.getSql());
        assertEquals(Arrays.asList("alice", false, 42L), spec.getParameters());
    }

    public void testInsertBuilderSupportsJsonbCastFields() {
        QuerySpec spec = DB.INSERT()
                .INTO("web_pages")
                .FIELD("tags", "[\"home\",\"news\"]", CastType.JSONB)
                .FIELD("link", "/about");

        assertEquals("INSERT INTO web_pages (tags, link) VALUES (?, ?)", spec.getSql());
        assertEquals(Arrays.asList("[\"home\",\"news\"]", "/about"), spec.getParameters());
    }

    public void testInsertBuilderSupportsOnConflictDoUpdate() {
        QuerySpec spec = DB.INSERT()
                .INTO("distributed_lock")
                .FIELD("name", "alice")
                .FIELD("locked_at = CURRENT_TIMESTAMP")
                .FIELD("lock_until = CURRENT_TIMESTAMP - INTERVAL '10 SECONDS' + INTERVAL '30 seconds'")
                .FIELD("uuid", "abc-123")
                .ON_CONFLICT("name")
                .DO_UPDATE()
                .SET("locked_at = EXCLUDED.locked_at")
                .SET("lock_until = EXCLUDED.lock_until")
                .SET("uuid = EXCLUDED.uuid")
                .WHERE("distributed_lock.name = EXCLUDED.name AND CURRENT_TIMESTAMP >= distributed_lock.lock_until");

        assertEquals(
                "INSERT INTO distributed_lock (name, locked_at, lock_until, uuid) VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '10 SECONDS' + INTERVAL '30 seconds', ?) " +
                        "ON CONFLICT (name) DO UPDATE SET locked_at = EXCLUDED.locked_at, lock_until = EXCLUDED.lock_until, uuid = EXCLUDED.uuid " +
                        "WHERE distributed_lock.name = EXCLUDED.name AND CURRENT_TIMESTAMP >= distributed_lock.lock_until",
                spec.getSql());
        assertEquals(Arrays.asList("alice", "abc-123"), spec.getParameters());
    }

    public void testInsertBuilderExecuteReturnsGeneratedKey() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("CREATE TABLE users (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            Long generatedId = DB.INSERT()
                    .INTO("users")
                    .FIELDS(new Field("name", "alice"))
                    .execute();

            assertEquals(Long.valueOf(1L), generatedId);
            assertEquals("alice", DB.SELECT("name")
                    .FROM("users")
                    .WHERE("id = ?", generatedId)
                    .executeQuery());
        }
    }

    public void testInsertBuilderReturnsSentinelWhenNoGeneratedKeyExists() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            DB.setDataSource(dataSource);

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("CREATE TABLE users_without_key (name VARCHAR(50))")) {
                statement.executeUpdate();
            }

            long generatedId = DB.INSERT()
                    .INTO("users_without_key")
                    .FIELDS(new Field("name", "alice"))
                    .execute();

            assertEquals(Insert.NO_GENERATED_KEY, generatedId);
            assertTrue(generatedId < 0L);
        }
    }

    public void testInsertBuilderOnConflictDoesNotRequestGeneratedKeys() {
        Insert insert = DB.INSERT()
                .INTO("distributed_lock")
                .FIELD("name", "alice")
                .FIELD("locked_at = CURRENT_TIMESTAMP")
                .FIELD("lock_until = CURRENT_TIMESTAMP")
                .FIELD("uuid", "abc-123")
                .ON_CONFLICT("name")
                .DO_UPDATE()
                .SET("locked_at = EXCLUDED.locked_at")
                .SET("lock_until = EXCLUDED.lock_until")
                .SET("uuid = EXCLUDED.uuid")
                .WHERE("distributed_lock.name = EXCLUDED.name AND CURRENT_TIMESTAMP >= distributed_lock.lock_until");

        assertFalse(insert.isReturnGeneratedKeys());
        assertTrue(insert.getSql().contains("ON CONFLICT"));
    }
}
