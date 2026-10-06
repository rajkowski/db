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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Stream query results directly to a CSV file while preserving JDBC parameter binding.
 */
public final class CsvExportHelper {

  private CsvExportHelper() {
  }

  /**
   * Writes the supplied query results to a CSV file using the provided column names.
   *
   * @param querySpec the SELECT query to stream
   * @param file the destination CSV file
   * @param columns the optional header names to write before the rows
   * @throws SQLException if the SQL cannot be executed
   * @throws IOException if the file cannot be written
   */
  public static void writeCsv(QuerySpec querySpec, File file, String... columns) throws SQLException, IOException {
    writeCsv(querySpec, file, StandardCharsets.UTF_8, columns);
  }

  /**
   * Writes the supplied query results to a CSV file using the provided column names and charset.
   *
   * @param querySpec the SELECT query to stream
   * @param file the destination CSV file
   * @param charset the encoding to use when writing the CSV file
   * @param columns the optional header names to write before the rows
   * @throws SQLException if the SQL cannot be executed
   * @throws IOException if the file cannot be written
   */
  public static void writeCsv(QuerySpec querySpec, File file, Charset charset, String... columns)
      throws SQLException, IOException {
    Objects.requireNonNull(querySpec, "querySpec cannot be null");
    Objects.requireNonNull(file, "file cannot be null");
    Objects.requireNonNull(charset, "charset cannot be null");

    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      if (!parent.mkdirs()) {
        throw new IOException("Could not create parent directory for CSV export: " + parent.getAbsolutePath());
      }
    }

    try (Connection connection = DB.getConnection();
        PreparedStatement statement = connection.prepareStatement(querySpec.getSql())) {
      DB.bindParameters(statement, querySpec.getParameters());
      try (ResultSet resultSet = statement.executeQuery()) {
        ResultSetMetaData metadata = resultSet.getMetaData();
        String[] headerNames = resolveHeaderNames(metadata, columns);
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), charset,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
          writeCsvRow(writer, headerNames);
          while (resultSet.next()) {
            String[] row = new String[metadata.getColumnCount()];
            for (int index = 0; index < row.length; index++) {
              Object value = resultSet.getObject(index + 1);
              row[index] = value == null ? "" : String.valueOf(value);
            }
            writeCsvRow(writer, row);
          }
        }
      }
    }
  }

  /**
   * Resolves the header names for the CSV file based on the provided columns and the ResultSet metadata.
   * 
   * @param metadata the ResultSet metadata to extract column information from
   * @param columns the optional column names provided by the user
   * @return an array of resolved header names for the CSV file
   * @throws SQLException if accessing the ResultSet metadata fails
   */
  private static String[] resolveHeaderNames(ResultSetMetaData metadata, String[] columns) throws SQLException {
    int columnCount = metadata.getColumnCount();
    if (columns != null && columns.length > 0 && columns.length == columnCount) {
      return columns.clone();
    }

    // When no header names are provided, derive them from the result metadata.
    String[] names = new String[columnCount];
    for (int index = 0; index < columnCount; index++) {
      String label = metadata.getColumnLabel(index + 1);
      names[index] = label == null || label.isBlank() ? metadata.getColumnName(index + 1) : label;
    }
    return names;
  }

  /**
   * Writes a single row of values to the CSV file, escaping as necessary.
   * 
   * @param writer the BufferedWriter to write the CSV row to
   * @param values the array of values representing the CSV row
   * @throws IOException if an I/O error occurs while writing the row
   */
  private static void writeCsvRow(BufferedWriter writer, String[] values) throws IOException {
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        writer.write(',');
      }
      String value = sanitizeCsvCell(values[i]);
      if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
        writer.write('"');
        writer.write(value.replace("\"", "\"\""));
        writer.write('"');
      } else {
        writer.write(value);
      }
    }
    writer.newLine();
  }

  /**
   * Escapes values that would be interpreted as Excel formulas when a CSV file is opened.
   *
   * Excel executes formulas when a cell begins with a formula trigger such as =, +, -, @, or a leading tab.
   * Prefixing the cell with a single quote keeps the value as text while preserving its visible content.
   *
   * @param value the raw cell value
   * @return the sanitized value safe for spreadsheet import
   */
  private static String sanitizeCsvCell(String value) {
    if (value == null) {
      return "";
    }
    if (value.isEmpty() || value.charAt(0) == '\'') {
      return value;
    }
    char firstChar = value.charAt(0);
    if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
        || firstChar == '\t' || firstChar == '\n' || firstChar == '\r') {
      return "'" + value;
    }
    return value;
  }

}
