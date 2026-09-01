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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a SQL DELETE query builder, allowing the construction of DELETE statements
 * with WHERE clauses and support for bound parameters and execution metrics.
 */
@SuppressWarnings("java:S100")
public class Delete extends QuerySpec {
  private String tableName;

  @Override
  public Delete METRICS(QueryMetrics metrics) {
    super.METRICS(metrics);
    return this;
  }

  private final List<String> whereClauses = new ArrayList<>();

  /**
   * Creates a DELETE statement without a table yet
   */
  public Delete() {
    // No table name yet; call FROM(table) later
  }

  /**
   * Adds the FROM table clause to the delete statement.
   *
   * @param table the table to delete from
   * @return this builder for chaining
   */
  public Delete FROM(String table) {
    this.tableName = sanitizeIdentifier(table);
    return this;
  }

  /**
   * Adds the FROM table clause to the delete statement.
   *
   * @param table the table to delete from
   * @return this builder for chaining
   */
  public Delete FROM(Table table) {
    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null.");
    }
    return FROM(table.getName());
  }

  /**
   * Adds a WHERE condition to the delete statement.
   *
   * @param clause the filter clause using ? placeholders
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public Delete WHERE(String clause, Object... values) {
    String normalized = sanitizeConditionClause(clause);
    whereClauses.add(normalized);
    if (values != null) {
      for (Object value : values) {
        parameters.add(value);
      }
    }
    return this;
  }

  /**
   * Adds an AND condition to the delete statement.
   *
   * @param clause the filter clause using ? placeholders
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public Delete AND(String clause, Object... values) {
    return WHERE(clause, values);
  }

  @Override
  public Boolean execute() {
    try {
      int rowsAffected = DB.executeUpdate(this);
      setRowsAffected(rowsAffected);
      return rowsAffected > 0;
    } catch (SQLException e) {
      // LOG.error("Error executing delete statement: " + getSql(), e);
      setRowsAffected(0);
      return false;
    }
  }

  @Override
  public Boolean execute(Connection connection) throws SQLException {
    int rowsAffected = DB.executeUpdate(connection, this);
    setRowsAffected(rowsAffected);
    return rowsAffected > 0;
  }

  @Override
  public String getSql() {
    StringBuilder builder = new StringBuilder("DELETE");
    if (tableName != null && !tableName.isEmpty()) {
      builder.append(" FROM ").append(tableName);
    }
    if (!whereClauses.isEmpty()) {
      builder.append(" WHERE ");
      for (int i = 0; i < whereClauses.size(); i++) {
        if (i > 0) {
          builder.append(" AND ");
        }
        builder.append(whereClauses.get(i));
      }
    }
    return builder.toString().trim();
  }
}
