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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import junit.framework.TestCase;

public class CsvExportHelperTest extends TestCase {

  private static String uniqueDbName(String baseName) {
    return baseName + "_" + System.nanoTime();
  }

  public void testCsvExportHelperStreamsSelectResultsToCsvFile() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_csv_export") + ";DB_CLOSE_DELAY=-1");
    config.setDriverClassName("org.h2.Driver");
    config.setUsername("sa");
    config.setPassword("");
    config.setMaximumPoolSize(2);
    config.setMinimumIdle(1);

    try (HikariDataSource dataSource = new HikariDataSource(config)) {
      DB.setDataSource(dataSource);

      try (Connection connection = dataSource.getConnection();
          PreparedStatement statement = connection.prepareStatement(
              "CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(50), active BOOLEAN)")) {
        statement.executeUpdate();
      }

      DB.INSERT().INTO("users").FIELDS(new Field("id", 1), new Field("name", "alice"), new Field("active", true))
          .execute();
      DB.INSERT().INTO("users").FIELDS(new Field("id", 2), new Field("name", "bob"), new Field("active", false))
          .execute();

      File csvFile = File.createTempFile("csv-export", ".csv");
      csvFile.deleteOnExit();

      CsvExportHelper.writeCsv(DB.SELECT("id", "name", "active").FROM("users").ORDER_BY("id ASC"),
          csvFile,
          new String[] { "id", "name", "active" });

      String csv = Files.readString(csvFile.toPath(), StandardCharsets.UTF_8);
      assertTrue(csv.contains("id,name,active"));
      assertTrue(csv.contains("1,alice,true"));
      assertTrue(csv.contains("2,bob,false"));
      assertTrue(csv.contains(System.lineSeparator()));
    }
  }

  public void testCsvExportHelperUsesResultSetMetadataWhenNoColumnsAreProvided() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_csv_metadata") + ";DB_CLOSE_DELAY=-1");
    config.setDriverClassName("org.h2.Driver");
    config.setUsername("sa");
    config.setPassword("");
    config.setMaximumPoolSize(2);
    config.setMinimumIdle(1);

    try (HikariDataSource dataSource = new HikariDataSource(config)) {
      DB.setDataSource(dataSource);

      try (Connection connection = dataSource.getConnection();
          PreparedStatement statement = connection.prepareStatement(
              "CREATE TABLE people (id INTEGER PRIMARY KEY, name VARCHAR(50))")) {
        statement.executeUpdate();
      }

      DB.INSERT().INTO("people").FIELDS(new Field("id", 7), new Field("name", "carol")).execute();

      File csvFile = File.createTempFile("csv-metadata", ".csv");
      csvFile.deleteOnExit();

      CsvExportHelper.writeCsv(DB.SELECT("id", "name").FROM("people").ORDER_BY("id ASC"), csvFile);

      String csv = Files.readString(csvFile.toPath(), StandardCharsets.UTF_8);
      String upperCsv = csv.toUpperCase(Locale.ROOT);
      assertTrue(upperCsv.contains("ID,NAME"));
      assertTrue(upperCsv.contains("7,CAROL"));
    }
  }

  public void testCsvExportHelperEscapesFormulaLikeValues() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:" + uniqueDbName("db_csv_formula") + ";DB_CLOSE_DELAY=-1");
    config.setDriverClassName("org.h2.Driver");
    config.setUsername("sa");
    config.setPassword("");
    config.setMaximumPoolSize(2);
    config.setMinimumIdle(1);

    try (HikariDataSource dataSource = new HikariDataSource(config)) {
      DB.setDataSource(dataSource);

      try (Connection connection = dataSource.getConnection();
          PreparedStatement statement = connection.prepareStatement(
              "CREATE TABLE alerts (id INTEGER PRIMARY KEY, note VARCHAR(100))")) {
        statement.executeUpdate();
      }

      DB.INSERT().INTO("alerts").FIELDS(new Field("id", 1), new Field("note", "=cmd|' /C calc'!A0"))
          .execute();

      File csvFile = File.createTempFile("csv-formula", ".csv");
      csvFile.deleteOnExit();

      CsvExportHelper.writeCsv(DB.SELECT("id", "note").FROM("alerts").ORDER_BY("id ASC"), csvFile,
          new String[] { "id", "note" });

      String csv = Files.readString(csvFile.toPath(), StandardCharsets.UTF_8);
      assertTrue(csv.contains("'="));
      assertTrue(csv.contains("=cmd|' /C calc'!A0"));
    }
  }
}
