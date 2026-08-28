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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Represents a SQL query specification, including the SQL text, bound parameters, and execution metrics.
 */
public class QuerySpec {
  protected final StringBuilder sql = new StringBuilder();
  protected final List<Object> parameters = new ArrayList<>();
  private QueryMetrics metrics = new QueryMetrics();

  public QueryMetrics getMetrics() {
    if (metrics == null) {
      metrics = new QueryMetrics();
    }
    metrics.setSql(getSql());
    metrics.setParameterCount(parameters.size());
    return metrics;
  }

  @SuppressWarnings("unchecked")
  public <T extends QuerySpec> T METRICS(QueryMetrics metrics) {
    if (metrics == null) {
      throw new IllegalArgumentException("Metrics cannot be null.");
    }
    this.metrics = metrics;
    this.metrics.setSql(getSql());
    this.metrics.setParameterCount(parameters.size());
    return (T) this;
  }

  public void setMetrics(QueryMetrics metrics) {
    METRICS(metrics);
  }

  /**
   * Returns the SQL text accumulated by the query builder with extra whitespace trimmed.
   *
   * @return the built SQL statement
   */
  public String getSql() {
    return sql.toString().trim();
  }

  /**
   * Returns the bound parameter values in the order they will be applied to the prepared statement.
   *
   * @return an immutable list of query parameters
   */
  public List<Object> getParameters() {
    return Collections.unmodifiableList(parameters);
  }

  /**
   * Indicates whether the most recent execution completed successfully.
   *
   * @return true when the last execution succeeded, false otherwise
   */
  public boolean isSuccess() {
    return metrics.isSuccess();
  }

  /**
   * Updates the success indicator for the last execution attempt.
   *
   * @param success true for successful execution, false for failure
   */
  public void setSuccess(boolean success) {
    metrics.setSuccess(success);
  }

  /**
   * Returns the number of rows affected by the most recent UPDATE or DELETE execution.
   *
   * @return the affected-row count, or 0 when no rows were changed or the query has not run yet
   */
  public int getRowsAffected() {
    return metrics.getRowsAffected();
  }

  /**
   * Sets the number of rows affected by the most recent update or delete execution.
   *
   * @param rowsAffected the number of affected rows from JDBC
   */
  public void setRowsAffected(int rowsAffected) {
    metrics.setRowsAffected(rowsAffected);
  }

  /**
   * Returns the last execution time in nanoseconds.
   *
   * @return time spent executing the query in nanoseconds
   */
  public long getExecutionTime() {
    return metrics.getExecutionTime();
  }

  /**
   * Sets the last measured execution time in nanoseconds.
   *
   * @param executionTime the elapsed time in nanoseconds
   */
  public void setExecutionTime(long executionTime) {
    metrics.setExecutionTime(executionTime);
  }

  /**
   * Returns the last status reported for the query.
   *
   * @return "SUCCESS" for successful execution or a failure label for rejected executions
   */
  public String getStatus() {
    return metrics.getStatus();
  }

  /**
   * Sets the status for the most recent execution attempt.
   *
   * @param status the outcome label for the last execution
   */
  public void setStatus(String status) {
    metrics.setStatus(status);
  }

  /**
   * Executes the query using the configured datasource and returns the first column from the first row for SELECT statements.
   *
   * @return the first column value or update count depending on the SQL statement type
   * @throws SQLException if execution fails
   */
  public Object execute() throws SQLException {
    return DB.execute(this);
  }

  /**
   * Executes the query using the supplied connection.
   *
   * @param connection the database connection to use
   * @return the first column value or update count depending on the SQL statement type
   * @throws SQLException if execution fails
   */
  public Object execute(Connection connection) throws SQLException {
    return DB.execute(connection, this);
  }

  /**
   * Executes the query and returns the first column from the first row for SELECT statements.
   *
   * @return the first column value or null if no rows are returned
   * @throws SQLException if execution fails
   */
  public Object executeQuery() throws SQLException {
    return DB.executeQuery(this);
  }

  /**
   * Executes the query with the supplied connection and returns the first column from the first row for SELECT statements.
   *
   * @param connection the database connection to use
   * @return the first column value or null if no rows are returned
   * @throws SQLException if execution fails
   */
  public Object executeQuery(Connection connection) throws SQLException {
    return DB.executeQuery(connection, this);
  }

  /**
   * Maps the first row of the query result into the requested type.
   *
   * @param mapper the mapping function for the result row
   * @param <T> the mapped result type
   * @return the mapped object or null when no row is returned
   */
  public <T> T returnRecord(DB.ResultSetMapper<T> mapper) {
    return DB.returnRecord(this, mapper);
  }

  /**
   * Maps the first row of the query result using the supplied connection.
   *
   * @param connection the database connection to use
   * @param mapper the mapping function for the result row
   * @param <T> the mapped result type
   * @return the mapped object or null when no row is returned
   */
  public <T> T returnRecord(Connection connection, DB.ResultSetMapper<T> mapper) {
    return DB.returnRecord(connection, this, mapper);
  }

  /**
   * Maps every row in the query result to objects of the requested type.
   *
   * @param mapper the mapping function for each row
   * @param <T> the mapped result type
   * @return a list of mapped rows
   */
  public <T> List<T> returnList(DB.ResultSetMapper<T> mapper) {
    return DB.returnList(this, mapper);
  }

  /**
   * Maps every row in the query result using the supplied connection.
   *
   * @param connection the database connection to use
   * @param mapper the mapping function for each row
   * @param <T> the mapped result type
   * @return a list of mapped rows
   */
  public <T> List<T> returnList(Connection connection, DB.ResultSetMapper<T> mapper) {
    return DB.returnList(connection, this, mapper);
  }

  /**
   * Maps every row in the query result to a DataResult wrapper with total count metadata.
   *
   * @param mapper the mapping function for each row
   * @param <T> the mapped result type
   * @return a DataResult containing the mapped rows and total record count
   */
  public <T> DataResult<T> returnDataResult(DB.ResultSetMapper<T> mapper) {
    return DB.returnDataResult(this, mapper);
  }

  /**
   * Maps every row in the query result using the supplied connection and wraps it in a DataResult.
   *
   * @param connection the database connection to use
   * @param mapper the mapping function for each row
   * @param <T> the mapped result type
   * @return a DataResult containing the mapped rows and total record count
   */
  public <T> DataResult<T> returnDataResult(Connection connection, DB.ResultSetMapper<T> mapper) {
    return DB.returnDataResult(connection, this, mapper);
  }

  /** Returns a single value from the first column of the first row, or null if no rows are returned, in the specified type */
  public <T> T returnValue(Class<T> type) {
    return DB.returnValue(this, type);
  }

  /** returnCount returns a long value for the select().count() query or -1 if error occurs; does not throw an exception */
  public long returnCount() {
    return DB.returnCount(this);
  }

  /**
   * Executes the query and collects the first column from each row into a list.
   *
   * @return a list of first-column values
   * @throws SQLException if execution fails
   */
  public List<Object> executeList() throws SQLException {
    return DB.executeList(this);
  }

  /**
   * Executes the query with the supplied connection and collects the first column from each row into a list.
   *
   * @param connection the database connection to use
   * @return a list of first-column values
   * @throws SQLException if execution fails
   */
  public List<Object> executeList(Connection connection) throws SQLException {
    return DB.executeList(connection, this);
  }

  /**
   * Appends a SQL fragment while ensuring it is separated from the previous fragment by a space when needed.
   *
   * @param fragment the SQL fragment to append
   */
  protected void appendSqlFragment(String fragment) {
    if (fragment == null || fragment.isEmpty()) {
      return;
    }
    if (sql.length() > 0 && !Character.isWhitespace(sql.charAt(sql.length() - 1))) {
      sql.append(' ');
    }
    sql.append(fragment);
  }

  /**
   * Adds a parameterized condition to the SQL buffer and stores the bound values in parameter order.
   *
   * @param connector the keyword preceding the condition, such as WHERE or AND
   * @param clause the SQL clause containing placeholders
   * @param values the values to bind to the clause
   */
  protected void appendCondition(String connector, String clause, Object... values) {
    String normalized = sanitizeConditionClause(clause);
    if (connector != null && !connector.isEmpty()) {
      appendSqlFragment(connector);
    }
    appendSqlFragment(normalized);
    if (values != null) {
      for (Object value : values) {
        parameters.add(value);
      }
    }
  }

  protected static String sanitizeConditionClause(String clause) {
    if (clause == null || clause.trim().isEmpty()) {
      throw new IllegalArgumentException("Condition clause cannot be empty.");
    }
    String normalized = clause.trim();
    if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*")
        || normalized.contains("*/")) {
      throw new IllegalArgumentException("Unsafe SQL fragment detected; use parameterized values only.");
    }
    if (!normalized.matches("[A-Za-z0-9_\\.\\s=<>!()?@%:,\\[\\]\\+\\-\\*\\|&'\\\"$\\/{}/\\\\]+")) {
      throw new IllegalArgumentException("Unsafe SQL fragment detected; use parameterized values only.");
    }
    String withoutQuotedLiterals = normalized.replaceAll("'([^']|'')*'", " ");
    String withoutSubqueries = stripSubqueryBodies(withoutQuotedLiterals);
    if (withoutSubqueries.matches(".*(?i)(^|[^A-Za-z0-9_])(true|false)(?=$|[^A-Za-z0-9_]).*")
        || withoutSubqueries.matches(".*(^|[^A-Za-z_])\\d+(?:\\.\\d+)?(?=$|[^A-Za-z0-9_]).*")) {
      throw new IllegalArgumentException("Unsafe SQL fragment detected; use parameterized values only.");
    }
    return normalized;
  }

  protected static String sanitizeAssignmentClause(String clause) {
    if (clause == null || clause.trim().isEmpty()) {
      throw new IllegalArgumentException("Assignment clause cannot be empty.");
    }
    String normalized = clause.trim();
    if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*")
        || normalized.contains("*/")) {
      throw new IllegalArgumentException("Unsafe SQL fragment detected; use parameterized values only.");
    }
    if (!normalized.matches("[A-Za-z0-9_\\.\\s=<>!()?@%:,\\[\\]\\+\\-\\*\\|&'\\\"$\\/{}/\\\\]+")) {
      throw new IllegalArgumentException("Unsafe SQL fragment detected; use parameterized values only.");
    }
    return normalized;
  }

  private static String stripSubqueryBodies(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    StringBuilder sanitized = new StringBuilder(text.length());
    int index = 0;
    while (index < text.length()) {
      int keywordStart = -1;
      int keywordLength = 0;
      for (int i = index; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (Character.isLetter(ch) || ch == '_') {
          int start = i;
          while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
            i++;
          }
          String token = text.substring(start, i).toLowerCase(Locale.ROOT);
          if (("exists".equals(token) || "select".equals(token) || "with".equals(token))
              && (i >= text.length() || Character.isWhitespace(text.charAt(i)) || text.charAt(i) == '(')) {
            keywordStart = start;
            keywordLength = i - start;
            break;
          }
          continue;
        }
      }
      if (keywordStart < 0) {
        sanitized.append(text.substring(index));
        break;
      }
      sanitized.append(text, index, keywordStart);
      int openParenIndex = keywordStart + keywordLength;
      while (openParenIndex < text.length() && Character.isWhitespace(text.charAt(openParenIndex))) {
        openParenIndex++;
      }
      if (openParenIndex >= text.length() || text.charAt(openParenIndex) != '(') {
        sanitized.append(text, keywordStart, text.length());
        break;
      }
      int closeParenIndex = findMatchingParenthesis(text, openParenIndex);
      if (closeParenIndex < 0) {
        sanitized.append(text, keywordStart, text.length());
        break;
      }
      sanitized.append(' ');
      index = closeParenIndex + 1;
    }
    return sanitized.toString();
  }

  private static int findMatchingParenthesis(String text, int openParenIndex) {
    int depth = 0;
    for (int i = openParenIndex; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '(') {
        depth++;
      } else if (ch == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Validates and normalizes a SQL identifier such as a table or column name.
   *
   * @param identifier the identifier to validate
   * @return the sanitized identifier
   */
  protected static String sanitizeIdentifier(String identifier) {
    if (identifier == null || identifier.trim().isEmpty()) {
      throw new IllegalArgumentException("Invalid SQL identifier: empty value.");
    }
    String trimmed = identifier.trim();
    if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_\\.]*")) {
      throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
    }
    return trimmed;
  }

  /**
   * Validates a SELECT expression before it is added to a query.
   *
   * @param expression the expression to validate
   * @return the sanitized expression
   */
  protected static String sanitizeSelectExpression(String expression) {
    if (expression == null || expression.trim().isEmpty()) {
      throw new IllegalArgumentException("Select expression cannot be empty.");
    }
    String trimmed = expression.trim();
    if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
      throw new IllegalArgumentException("Unsafe SQL expression detected.");
    }
    if (!trimmed.matches("[A-Za-z0-9_.*()\\s,=<>!?:'\"%+\\-\\[\\]@|&$/{}/\\\\]+")) {
      throw new IllegalArgumentException("Unsafe SQL expression detected.");
    }
    return trimmed;
  }

  /**
   * Validates an ORDER BY clause before it is appended to the SQL.
   *
   * @param clause the ORDER BY clause to validate
   * @return the sanitized clause
   */
  protected static String sanitizeOrderBy(String clause) {
    if (clause == null || clause.trim().isEmpty()) {
      throw new IllegalArgumentException("ORDER BY clause cannot be empty.");
    }
    String trimmed = clause.trim();
    if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
      throw new IllegalArgumentException("Unsafe ORDER BY clause detected.");
    }
    if (!trimmed.matches("[A-Za-z0-9_.*()\\s,=<>!?:'\"%+\\-\\[\\]@|&$/{}/\\\\]+")) {
      throw new IllegalArgumentException("Unsafe ORDER BY clause detected.");
    }
    return trimmed;
  }

  /**
   * Reads the underlying value from a Field object in the same type-safe order used by parameter binding.
   *
   * @param field the field whose value should be extracted
   * @return the field's raw value, or null when no value is present
   */
  protected static Object readFieldValue(Field field) {
    if (field == null || field.isNull()) {
      return null;
    }
    if (field.getStringValue() != null) {
      return field.getStringValue();
    }
    if (field.getLongValue() != null) {
      return field.getLongValue();
    }
    if (field.getIntValue() != null) {
      return field.getIntValue();
    }
    if (field.getDoubleValue() != null) {
      return field.getDoubleValue();
    }
    if (field.getBooleanValue() != null) {
      return field.getBooleanValue();
    }
    if (field.getBigDecimalValue() != null) {
      return field.getBigDecimalValue();
    }
    if (field.getTimestampValue() != null) {
      return field.getTimestampValue();
    }
    if (field.getObjectValues() != null) {
      return field.getObjectValues();
    }
    if (field.getStringValues() != null) {
      return field.getStringValues();
    }
    if (field.getLongValues() != null) {
      return field.getLongValues();
    }
    if (field.getTimestampValues() != null) {
      return field.getTimestampValues();
    }
    return null;
  }
}
