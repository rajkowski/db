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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.postgresql.util.PGobject;

/**
 * Utility class for database operations, including tenant-aware datasource management
 * and thread-local connection handling.
 */
@SuppressWarnings("java:S100")
public final class DB {

  private static final Log log = LogFactory.getLog(DB.class);

  private static volatile DataSource dataSource;
  private static volatile TenantRegistry tenantRegistry = new TenantRegistry();
  private static final ThreadLocal<DataSource> tenantDataSource = new ThreadLocal<>();
  private static final ThreadLocal<Connection> threadLocalConnection = new ThreadLocal<>();
  private static final ThreadLocal<String> tenantId = new ThreadLocal<>();

  private DB() {
  }

  /**
   * Sets the default DataSource used by queries when no tenant or thread-local context is active.
   *
   * @param dataSource the datasource to use for all standard database operations
   */
  public static void setDataSource(DataSource dataSource) {
    if (dataSource == null) {
      throw new IllegalArgumentException("DataSource cannot be null");
    }
    DB.dataSource = dataSource;
  }

  /**
   * Returns the active datasource, preferring the tenant-scoped datasource when one is set.
   *
   * @return the current datasource for the active request context, or null if none is configured
   */
  public static DataSource getDataSource() {
    DataSource scopedDataSource = tenantDataSource.get();
    if (scopedDataSource != null) {
      return scopedDataSource;
    }
    return dataSource;
  }

  /**
   * Replaces the registry responsible for resolving tenant-specific datasources.
   *
   * @param tenantRegistry the tenant registry to use
   */
  public static void setTenantRegistry(TenantRegistry tenantRegistry) {
    if (tenantRegistry == null) {
      throw new IllegalArgumentException("TenantRegistry cannot be null");
    }
    DB.tenantRegistry = tenantRegistry;
  }

  /**
   * Returns the configured tenant registry.
   *
   * @return the current tenant registry
   */
  public static TenantRegistry getTenantRegistry() {
    return tenantRegistry;
  }

  /**
   * Temporarily sets the datasource for the current tenant-scoped execution context.
   *
   * @param tenantDataSource the tenant datasource to activate in the current thread
   */
  public static void setTenantDataSource(DataSource tenantDataSource) {
    if (tenantDataSource == null) {
      throw new IllegalArgumentException("DataSource cannot be null");
    }
    DB.tenantDataSource.set(tenantDataSource);
  }

  /**
   * Returns the tenant-scoped datasource for the current thread, if any.
   *
   * @return the tenant datasource for the active thread, or null if unset
   */
  public static DataSource getTenantDataSource() {
    return tenantDataSource.get();
  }

  /**
   * Clears any tenant-scoped datasource and tenant id set on the current thread.
   */
  public static void clearTenantDataSource() {
    tenantDataSource.remove();
    tenantId.remove();
  }

  /**
   * Returns the tenant id currently selected for the active request or thread-local scope.
   *
   * @return the active tenant id, or null if no tenant is currently selected
   */
  public static String getTenantId() {
    return tenantId.get();
  }

  /**
   * Selects a tenant for the current request/thread context by resolving it from the configured registry.
   *
   * @param tenantIdValue the tenant id to make active for subsequent database calls
   */
  public static void setTenant(String tenantIdValue) {
    if (tenantIdValue == null || tenantIdValue.isBlank()) {
      throw new IllegalArgumentException("Tenant id cannot be null or blank");
    }
    if (tenantRegistry == null) {
      throw new IllegalStateException(
          "No TenantRegistry configured. Initialize DB.setTenantRegistry(...) before using tenant-scoped queries.");
    }
    DataSource tenantDataSourceForId = tenantRegistry.getDataSource(tenantIdValue);
    if (tenantDataSourceForId == null) {
      throw new IllegalStateException("No DataSource registered for tenant: " + tenantIdValue);
    }
    DB.tenantId.set(tenantIdValue);
    DB.tenantDataSource.set(tenantDataSourceForId);
  }

  /**
   * Clears the current tenant selection for this thread/request context.
   */
  public static void clearTenant() {
    tenantDataSource.remove();
    tenantId.remove();
  }

  /**
   * Runs the provided code block while a tenant datasource is temporarily active.
   *
   * @param tenantDataSource the datasource to use for the duration of the block
   * @param runnable code to execute under the tenant datasource
   */
  public static void withTenantDataSource(DataSource tenantDataSource, Runnable runnable) {
    if (tenantDataSource == null) {
      throw new IllegalArgumentException("DataSource cannot be null");
    }
    DataSource previous = DB.tenantDataSource.get();
    String previousTenantId = DB.tenantId.get();
    try {
      DB.tenantDataSource.set(tenantDataSource);
      DB.tenantId.remove();
      runnable.run();
    } finally {
      if (previous == null) {
        DB.tenantDataSource.remove();
      } else {
        DB.tenantDataSource.set(previous);
      }
      if (previousTenantId == null) {
        DB.tenantId.remove();
      } else {
        DB.tenantId.set(previousTenantId);
      }
    }
  }

  /**
   * Runs code under a temporary datasource context using the same behavior as tenant-scoped datasource binding.
   *
   * @param dataSource the datasource to activate for the duration of the callback
   * @param runnable the callback to execute
   */
  public static void withDataSource(DataSource dataSource, Runnable runnable) {
    withTenantDataSource(dataSource, runnable);
  }

  /**
   * Runs the supplied code while a specific JDBC connection is bound to the current thread.
   *
   * @param connection the connection to make active for the duration of the callback
   * @param runnable the callback to execute with the connection in scope
   */
  public static void withConnection(Connection connection, Runnable runnable) {
    if (connection == null) {
      throw new IllegalArgumentException("Connection cannot be null");
    }
    Connection previous = DB.threadLocalConnection.get();
    try {
      DB.threadLocalConnection.set(connection);
      runnable.run();
    } finally {
      if (previous == null) {
        DB.threadLocalConnection.remove();
      } else {
        DB.threadLocalConnection.set(previous);
      }
    }
  }

  /**
   * Stores a thread-local JDBC connection for the current execution context.
   *
   * @param connection the connection to bind to the current thread
   */
  public static void setThreadLocalConnection(Connection connection) {
    if (connection == null) {
      throw new IllegalArgumentException("Connection cannot be null");
    }
    threadLocalConnection.set(connection);
  }

  /**
   * Returns the thread-local connection currently bound to this thread, if any.
   *
   * @return the active thread-local connection or null
   */
  public static Connection getThreadLocalConnection() {
    return threadLocalConnection.get();
  }

  /**
   * Removes the thread-local JDBC connection from the current thread.
   */
  public static void clearThreadLocalConnection() {
    threadLocalConnection.remove();
  }

  /**
   * Executes the provided runnable while a tenant id and datasource are temporarily active for the current thread.
   *
   * @param tenantIdValue the tenant identifier to resolve in the registry
   * @param runnable the work to execute within the tenant scope
   */
  public static void withTenant(String tenantIdValue, Runnable runnable) {
    if (tenantIdValue == null || tenantIdValue.isBlank()) {
      throw new IllegalArgumentException("Tenant id cannot be null or blank");
    }
    if (tenantRegistry == null) {
      throw new IllegalStateException(
          "No TenantRegistry configured. Initialize DB.setTenantRegistry(...) before using tenant-scoped queries.");
    }
    DataSource tenantDataSourceForId = tenantRegistry.getDataSource(tenantIdValue);
    if (tenantDataSourceForId == null) {
      throw new IllegalStateException("No DataSource registered for tenant: " + tenantIdValue);
    }
    String previousTenantId = DB.tenantId.get();
    DataSource previousDataSource = DB.tenantDataSource.get();
    try {
      DB.tenantId.set(tenantIdValue);
      DB.tenantDataSource.set(tenantDataSourceForId);
      runnable.run();
    } finally {
      if (previousDataSource == null) {
        DB.tenantDataSource.remove();
      } else {
        DB.tenantDataSource.set(previousDataSource);
      }
      if (previousTenantId == null) {
        DB.tenantId.remove();
      } else {
        DB.tenantId.set(previousTenantId);
      }
    }
  }

  /**
   * Starts a transaction on the supplied connection and returns the helper that restores the original auto-commit value upon close.
   *
   * @param connection the connection to place into a transaction
   * @return an AutoStartTransaction wrapper for the connection
   * @throws SQLException if the connection cannot be placed into transaction mode
   */
  public static AutoStartTransaction startTransaction(Connection connection) throws SQLException {
    if (connection == null) {
      throw new IllegalArgumentException("Connection cannot be null");
    }
    return new AutoStartTransaction(connection);
  }

  /**
   * Creates a transactional rollback helper for the supplied connection.
   *
   * @param connection the connection to recover from a failed transaction
   * @return an AutoRollback helper that rolls the transaction back unless commit() is called
   */
  public static AutoRollback rollback(Connection connection) {
    if (connection == null) {
      throw new IllegalArgumentException("Connection cannot be null");
    }
    return new AutoRollback(connection);
  }

  /**
   * Opens a connection from the supplied datasource, starts a transaction, runs the callback, and commits on success.
   * If the callback throws any runtime exception, the transaction is rolled back automatically.
   *
   * @param dataSource the datasource to obtain a connection from
   * @param runnable the transaction work to execute
   * @throws SQLException if the transaction cannot be started or the connection cannot be opened
   */
  public static void withTransaction(DataSource dataSource, Runnable runnable) throws SQLException {
    if (dataSource == null) {
      throw new IllegalArgumentException("DataSource cannot be null");
    }
    if (runnable == null) {
      throw new IllegalArgumentException("Runnable cannot be null");
    }
    try (Connection connection = dataSource.getConnection()) {
      withConnection(connection, () -> {
        try (AutoStartTransaction transaction = startTransaction(connection);
            AutoRollback autoRollback = rollback(connection)) {
          runnable.run();
          autoRollback.commit();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  /**
   * Runs the supplied callback within an active transaction on the provided connection.
   * Any runtime exception triggers an automatic rollback.
   *
   * @param connection the connection to use for the transaction
   * @param runnable the code to run under the transaction
   * @throws SQLException if the transaction cannot be started
   */
  public static void withTransaction(Connection connection, Runnable runnable) throws SQLException {
    if (connection == null) {
      throw new IllegalArgumentException("Connection cannot be null");
    }
    if (runnable == null) {
      throw new IllegalArgumentException("Runnable cannot be null");
    }
    withConnection(connection, () -> {
      try (AutoStartTransaction transaction = startTransaction(connection);
          AutoRollback autoRollback = rollback(connection)) {
        runnable.run();
        autoRollback.commit();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * Resolves the active JDBC connection from the thread-local or datasource context.
   *
   * @return a live database connection for the current execution context
   * @throws SQLException if the connection cannot be opened
   */
  public static Connection getConnection() throws SQLException {
    Connection currentThreadConnection = threadLocalConnection.get();
    if (currentThreadConnection != null) {
      return currentThreadConnection;
    }

    DataSource currentDataSource = getDataSource();
    if (currentDataSource == null) {
      throw new IllegalStateException(
          "No DataSource configured. Initialize DB.setDataSource(...) or DB.setTenantRegistry(...) before executing queries.");
    }
    return currentDataSource.getConnection();
  }

  /**
   * Starts a SELECT query for the given columns.
   *
   * @param columns the columns to select; if not supplied, the query selects all columns
   * @return a fluent SelectBuilder for the query
   */
  public static Select SELECT(String... columns) {
    return new Select(columns);
  }

  /**
   * Starts a SELECT query that selects all columns.
   *
   * @return a fluent SelectBuilder for the query
   */
  public static Select SELECT() {
    return new Select();
  }

  /**
   * Starts an INSERT statement builder.
   *
   * @return a fluent InsertBuilder
   */
  public static Insert INSERT() {
    return new Insert();
  }

  /**
   * Creates a validated table value object.
   *
   * @param name the table name
   * @return a validated table value
   */
  public static Table TABLE(String name) {
    return Table.of(name);
  }

  /**
   * Creates a validated alias value object.
   *
   * @param alias the alias to validate
   * @return a validated alias value
   */
  public static AS AS(String alias) {
    return new AS(alias);
  }

  /**
   * Starts an UPDATE statement for the specified table.
   *
   * @param table the table to update
   * @return a fluent UpdateBuilder
   */
  public static Update UPDATE(String table) {
    return new Update(table);
  }

  /**
   * Starts an UPDATE statement for the specified table.
   *
   * @param table the table to update
   * @return a fluent UpdateBuilder
   */
  public static Update UPDATE(Table table) {
    return new Update(table);
  }

  /**
   * Starts a DELETE statement without a table name.
   *
   * @return a fluent DeleteBuilder
   */
  public static Delete DELETE() {
    return new Delete();
  }

  /**
   * Builds a COUNT aggregate query for the supplied expression.
   *
   * @param expression the column or expression to aggregate
   * @return a fluent AggregateBuilder
   */
  public static AggregateBuilder COUNT(String expression) {
    return new AggregateBuilder("COUNT", expression);
  }

  /**
   * Builds a SUM aggregate query for the supplied expression.
   *
   * @param expression the column or expression to aggregate
   * @return a fluent AggregateBuilder
   */
  public static AggregateBuilder SUM(String expression) {
    return new AggregateBuilder("SUM", expression);
  }

  /**
   * Builds an AVG aggregate query for the supplied expression.
   *
   * @param expression the column or expression to aggregate
   * @return a fluent AggregateBuilder
   */
  public static AggregateBuilder AVG(String expression) {
    return new AggregateBuilder("AVG", expression);
  }

  /**
   * Builds a MIN aggregate query for the supplied expression.
   *
   * @param expression the column or expression to aggregate
   * @return a fluent AggregateBuilder
   */
  public static AggregateBuilder MIN(String expression) {
    return new AggregateBuilder("MIN", expression);
  }

  /**
   * Builds a MAX aggregate query for the supplied expression.
   *
   * @param expression the column or expression to aggregate
   * @return a fluent AggregateBuilder
   */
  public static AggregateBuilder MAX(String expression) {
    return new AggregateBuilder("MAX", expression);
  }

  @FunctionalInterface
  public interface ResultSetMapper<T> {
    /**
     * Converts a single row from a ResultSet into a domain object.
     *
     * @param resultSet the current row being mapped
     * @return the mapped object
     * @throws SQLException if mapping fails while reading the result set
     */
    T map(ResultSet resultSet) throws SQLException;
  }

  private static void markQuerySuccess(QuerySpec querySpec) {
    if (querySpec == null) {
      return;
    }
    querySpec.setSuccess(true);
    querySpec.setStatus("SUCCESS");
    QueryMetrics metrics = querySpec.getMetrics();
    metrics.setSql(querySpec.getSql());
    metrics.setParameterCount(querySpec.getParameters().size());
  }

  private static void markQueryFailure(QuerySpec querySpec) {
    if (querySpec == null) {
      return;
    }
    querySpec.setSuccess(false);
    querySpec.setStatus("FAILED");
    QueryMetrics metrics = querySpec.getMetrics();
    metrics.setSql(querySpec.getSql());
    metrics.setParameterCount(querySpec.getParameters().size());
  }

  private static void logQueryException(String action, QuerySpec querySpec, SQLException exception) {
    if (exception == null) {
      return;
    }
    if (querySpec == null) {
      log.warn("Failed to " + action + ". QuerySpec was null.", exception);
      return;
    }
    log.warn("Failed to " + action + " query: " + querySpec.getSql(), exception);
  }

  private static void markExecutionTime(QuerySpec querySpec, long startedNanos) {
    if (querySpec == null) {
      return;
    }
    querySpec.setExecutionTime(System.nanoTime() - startedNanos);
    QueryMetrics metrics = querySpec.getMetrics();
    metrics.setSql(querySpec.getSql());
    metrics.setParameterCount(querySpec.getParameters().size());
  }

  /**
   * Determines whether the supplied ResultSet contains a column with the given name.
   *
   * @param resultSet the result set to inspect
   * @param columnName the logical column name to test for
   * @return true when the column exists in the current result set
   */
  public static boolean hasColumn(ResultSet resultSet, String columnName) {
    if (resultSet == null || columnName == null || columnName.isBlank()) {
      return false;
    }
    try {
      resultSet.findColumn(columnName);
      return true;
    } catch (SQLException e) {
      return false;
    }
  }

  /**
   * Reads a numeric ResultSet value and returns the fallback when the field is SQL NULL.
   *
   * @param resultSet the current result set
   * @param field the column name to read
   * @param valueWhenNull the fallback value for SQL NULL
   * @return the long value or the provided fallback
   * @throws SQLException if the field cannot be read
   */
  public static long getLong(ResultSet resultSet, String field, long valueWhenNull) throws SQLException {
    long value = resultSet.getLong(field);
    if (resultSet.wasNull()) {
      return valueWhenNull;
    }
    return value;
  }

  /**
   * Reads a numeric ResultSet value and returns the fallback when the field is SQL NULL.
   *
   * @param resultSet the current result set
   * @param field the column name to read
   * @param valueWhenNull the fallback value for SQL NULL
   * @return the double value or the provided fallback
   * @throws SQLException if the field cannot be read
   */
  public static double getDouble(ResultSet resultSet, String field, double valueWhenNull) throws SQLException {
    double value = resultSet.getDouble(field);
    if (resultSet.wasNull()) {
      return valueWhenNull;
    }
    return value;
  }

  /**
   * Reads a numeric ResultSet value and returns the fallback when the field is SQL NULL.
   *
   * @param resultSet the current result set
   * @param field the column name to read
   * @param valueWhenNull the fallback value for SQL NULL
   * @return the int value or the provided fallback
   * @throws SQLException if the field cannot be read
   */
  public static int getInt(ResultSet resultSet, String field, int valueWhenNull) throws SQLException {
    int value = resultSet.getInt(field);
    if (resultSet.wasNull()) {
      return valueWhenNull;
    }
    return value;
  }

  /**
   * Converts a PostgreSQL interval to ISO-8601 duration form, returning null when the field is SQL NULL.
   *
   * @param resultSet the current result set
   * @param field the column name to read
   * @return ISO-8601 formatted duration or null for SQL NULL
   * @throws SQLException if the value cannot be read
   */
  public static String getPeriod(ResultSet resultSet, String field) throws SQLException {
    Object value = resultSet.getObject(field);
    if (value == null || resultSet.wasNull()) {
      return null;
    }
    if (!(value instanceof org.postgresql.util.PGInterval pgi)) {
      return value.toString();
    }
    StringBuilder sb = new StringBuilder("P");
    if (pgi.getYears() > 0) {
      sb.append(pgi.getYears()).append("Y");
    }
    if (pgi.getMonths() > 0) {
      sb.append(pgi.getMonths()).append("M");
    }
    if (pgi.getDays() > 0) {
      sb.append(pgi.getDays()).append("D");
    }
    if (sb.length() > 1 && pgi.getHours() == 0 && pgi.getMinutes() == 0 && pgi.getWholeSeconds() == 0) {
      return sb.toString();
    }
    sb.append("T");
    boolean foundTime = false;
    if (pgi.getHours() > 0) {
      foundTime = true;
      sb.append(pgi.getHours()).append("H");
    }
    if (pgi.getMinutes() > 0) {
      foundTime = true;
      sb.append(pgi.getMinutes()).append("M");
    }
    if (!foundTime || pgi.getWholeSeconds() > 0) {
      sb.append(pgi.getWholeSeconds()).append("S");
    }
    return sb.toString();
  }

  /**
   * Executes a query and returns either a single value or the affected row count depending on the SQL type.
   *
   * @param querySpec the query specification to run
   * @return the value for SELECT queries or the update count for non-SELECT queries
   * @throws SQLException if the query fails to execute
   */
  public static Object execute(QuerySpec querySpec) throws SQLException {
    Connection connection = getConnection();
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      return execute(connection, querySpec);
    } finally {
      if (closeConnection) {
        connection.close();
      }
    }
  }

  /**
   * Executes a query against the supplied connection and returns the first-column value for SELECT statements.
   *
   * @param connection the JDBC connection to use
   * @param querySpec the query specification to run
   * @return the first-column value for SELECT queries or the update count otherwise
   * @throws SQLException if execution fails
   */
  public static Object execute(Connection connection, QuerySpec querySpec) throws SQLException {
    String sql = querySpec.getSql();
    if (isSelectSql(sql)) {
      return executeQuery(connection, querySpec);
    }
    if (isInsertSql(sql)) {
      return executeInsert(connection, querySpec);
    }
    return executeUpdate(connection, querySpec);
  }

  /**
   * Executes the query and maps the first matching row into a mapped object.
   *
   * @param querySpec the query to execute
   * @param mapper the mapper that converts a result row into the target type
   * @param <T> the mapped result type
   * @return the mapped row or null when no row matches
   */
  public static <T> T returnRecord(QuerySpec querySpec, ResultSetMapper<T> mapper) {
    Connection connection;
    try {
      connection = getConnection();
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("open connection for record", querySpec, e);
      return null;
    }
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      return returnRecord(connection, querySpec, mapper);
    } finally {
      if (closeConnection) {
        try {
          connection.close();
        } catch (SQLException e) {
          // best effort close; query result already handled
        }
      }
    }
  }

  /**
   * Maps the first row returned by the supplied query into a value object using the provided mapper.
   *
   * @param connection the JDBC connection to use
   * @param querySpec the query to execute
   * @param mapper the row-to-object mapping function
   * @param <T> the mapped result type
   * @return the mapped row or null when there is no result
   */
  public static <T> T returnRecord(Connection connection, QuerySpec querySpec, ResultSetMapper<T> mapper) {
    if (mapper == null) {
      throw new IllegalArgumentException("Mapper cannot be null.");
    }
    long startedNanos = System.nanoTime();
    try {
      try (PreparedStatement statement = connection.prepareStatement(querySpec.getSql())) {
        bindParameters(statement, querySpec.getParameters());
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            T value = mapper.map(resultSet);
            markQuerySuccess(querySpec);
            return value;
          }
        }
      }
      markQuerySuccess(querySpec);
      return null;
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("map record", querySpec, e);
      return null;
    } finally {
      markExecutionTime(querySpec, startedNanos);
    }
  }

  /**
   * Executes the query and returns every mapped row as a list.
   *
   * @param querySpec the query to execute
   * @param mapper the row-to-object mapper
   * @param <T> the mapped result type
   * @return a list of mapped rows
   */
  public static <T> List<T> returnList(QuerySpec querySpec, ResultSetMapper<T> mapper) {
    Connection connection;
    try {
      connection = getConnection();
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("open connection for list", querySpec, e);
      return new ArrayList<>();
    }
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      return returnList(connection, querySpec, mapper);
    } finally {
      if (closeConnection) {
        try {
          connection.close();
        } catch (SQLException e) {
          // best effort close; rows already returned
        }
      }
    }
  }

  /**
   * Executes the query against the given connection and returns all mapped rows.
   *
   * @param connection the JDBC connection to use
   * @param querySpec the query to execute
   * @param mapper the row-to-object mapper
   * @param <T> the mapped result type
   * @return a list of mapped rows
   */
  public static <T> List<T> returnList(Connection connection, QuerySpec querySpec, ResultSetMapper<T> mapper) {
    if (mapper == null) {
      throw new IllegalArgumentException("Mapper cannot be null.");
    }
    List<T> rows = new ArrayList<>();
    long startedNanos = System.nanoTime();
    try {
      try (PreparedStatement statement = connection.prepareStatement(querySpec.getSql())) {
        bindParameters(statement, querySpec.getParameters());
        try (ResultSet resultSet = statement.executeQuery()) {
          while (resultSet.next()) {
            rows.add(mapper.map(resultSet));
          }
        }
      }
      applyPagingMetadata(connection, querySpec);
      markQuerySuccess(querySpec);
      return rows;
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("return list", querySpec, e);
      return rows;
    } finally {
      markExecutionTime(querySpec, startedNanos);
    }
  }

  /**
   * Executes the query and returns every mapped row wrapped in a DataResult.
   *
   * @param querySpec the query to execute
   * @param mapper the row-to-object mapper
   * @param <T> the mapped result type
   * @return a DataResult containing mapped rows and total count metadata
   * @throws SQLException if the query or mapping fails
   */
  public static <T> DataResult<T> returnDataResult(QuerySpec querySpec, ResultSetMapper<T> mapper) {
    Connection connection;
    try {
      connection = getConnection();
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      return new DataResult<>();
    }
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      return returnDataResult(connection, querySpec, mapper);
    } finally {
      if (closeConnection) {
        try {
          connection.close();
        } catch (SQLException e) {
          // best effort close; DataResult already returned
        }
      }
    }
  }

  /**
   * Executes the query against the given connection and wraps all mapped rows in a DataResult.
   *
   * @param connection the JDBC connection to use
   * @param querySpec the query to execute
   * @param mapper the row-to-object mapper
   * @param <T> the mapped result type
   * @return a DataResult containing mapped rows and total count metadata
   * @throws SQLException if the query or mapping fails
   */
  public static <T> DataResult<T> returnDataResult(Connection connection, QuerySpec querySpec,
      ResultSetMapper<T> mapper) {
    if (mapper == null) {
      throw new IllegalArgumentException("Mapper cannot be null.");
    }
    DataResult<T> result = new DataResult<>();
    result.setRecords(returnList(connection, querySpec, mapper));
    if (querySpec instanceof Select) {
      Select select = (Select) querySpec;
      if (select.getPaging() != null) {
        result.setTotalRecordCount(select.getPaging().getTotalCount());
      } else if (select.getDataConstraints() != null) {
        result.setTotalRecordCount(select.getDataConstraints().getTotalRecordCount());
      }
    }
    return result;
  }

  // DB.returnValue(this, type); be generic and without throwing an exception
  public static <T> T returnValue(QuerySpec querySpec, Class<T> type) {
    Connection connection;
    try {
      connection = getConnection();
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("open connection for value", querySpec, e);
      return null;
    }
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      Object value = executeQuery(connection, querySpec);
      if (value == null) {
        return null;
      }
      if (type.isInstance(value)) {
        return type.cast(value);
      }
      throw new IllegalArgumentException(
          "Cannot convert value of type " + value.getClass().getName() + " to " + type.getName());
    } finally {
      if (closeConnection) {
        try {
          connection.close();
        } catch (SQLException e) {
          // best effort close; count already returned
        }
      }
    }
  }

  /** returnCount returns a long value for the select().count() query or -1 if error occurs; does not throw an exception */
  public static long returnCount(QuerySpec querySpec) {
    Connection connection;
    try {
      connection = getConnection();
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("open connection for count", querySpec, e);
      return -1;
    }
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      Object result = executeQuery(connection, querySpec);
      if (result instanceof Number number) {
        return number.longValue();
      }
      return -1;
    } finally {
      if (closeConnection) {
        try {
          connection.close();
        } catch (SQLException e) {
          // best effort close; count already returned
        }
      }
    }
  }

  /**
   * Executes the query and returns the first column from the first row as a single value.
   *
   * @param querySpec the query to execute
   * @return the first column value or null when no rows are returned
   * @throws SQLException if execution fails
   */
  public static Object executeQuery(QuerySpec querySpec) throws SQLException {
    Connection connection = getConnection();
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      return executeQuery(connection, querySpec);
    } finally {
      if (closeConnection) {
        connection.close();
      }
    }
  }

  /**
   * Executes the query against the provided connection and returns the first value from the first row.
   *
   * @param connection the JDBC connection to use
   * @param querySpec the query to execute
   * @return the first column value or null when no rows are returned
   * @throws SQLException if execution fails
   */
  public static Object executeQuery(Connection connection, QuerySpec querySpec) {
    long startedNanos = System.nanoTime();
    try {
      try (PreparedStatement statement = connection.prepareStatement(querySpec.getSql())) {
        bindParameters(statement, querySpec.getParameters());
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            Object value = resultSet.getObject(1);
            markQuerySuccess(querySpec);
            return value;
          }
        }
      }
      markQuerySuccess(querySpec);
      return null;
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("execute query", querySpec, e);
      return null;
    } finally {
      markExecutionTime(querySpec, startedNanos);
    }
  }

  /**
   * Executes a non-SELECT statement and returns the affected row count.
   *
   * @param querySpec the query to run
   * @return the number of rows affected by the statement
   * @throws SQLException if execution fails
   */
  public static int executeUpdate(QuerySpec querySpec) throws SQLException {
    Connection connection = getConnection();
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      return executeUpdate(connection, querySpec);
    } finally {
      if (closeConnection) {
        connection.close();
      }
    }
  }

  /**
   * Executes the provided update statement on the supplied connection.
   *
   * @param connection the JDBC connection to use
   * @param querySpec the update specification to execute
   * @return the number of rows affected by the statement
   * @throws SQLException if execution fails
   */
  public static int executeUpdate(Connection connection, QuerySpec querySpec) throws SQLException {
    long startedNanos = System.nanoTime();
    try (PreparedStatement statement = connection.prepareStatement(querySpec.getSql())) {
      bindParameters(statement, querySpec.getParameters());
      int rowsAffected = statement.executeUpdate();
      querySpec.setRowsAffected(rowsAffected);
      markQuerySuccess(querySpec);
      return rowsAffected;
    } catch (SQLException e) {
      querySpec.setRowsAffected(0);
      markQueryFailure(querySpec);
      logQueryException("execute update", querySpec, e);
      throw (e);
    } finally {
      markExecutionTime(querySpec, startedNanos);
    }
  }

  /**
   * Executes an INSERT statement and returns the generated sequence value from the database.
   *
   * @param querySpec the insert statement to execute
   * @return the generated key value for the insert or NO_GENERATED_KEY when the database does not return one
   */
  public static long executeInsert(QuerySpec querySpec) {
    Connection connection;
    try {
      connection = getConnection();
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      return Insert.NO_GENERATED_KEY;
    }
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      return executeInsert(connection, querySpec);
    } finally {
      if (closeConnection) {
        try {
          connection.close();
        } catch (SQLException e) {
          // best effort close; outcome already reported via metrics
        }
      }
    }
  }

  /**
   * Executes the supplied insert statement on the given connection and returns the generated key.
   *
   * @param connection the JDBC connection to use
   * @param querySpec the insert specification to execute
   * @return the generated key value or NO_GENERATED_KEY when the database does not return one
   */
  public static long executeInsert(Connection connection, QuerySpec querySpec) {
    long startedNanos = System.nanoTime();
    boolean returnGeneratedKeys = querySpec instanceof Insert && ((Insert) querySpec).isReturnGeneratedKeys();
    try {
      PreparedStatement statement;
      if (returnGeneratedKeys) {
        statement = connection.prepareStatement(querySpec.getSql(), PreparedStatement.RETURN_GENERATED_KEYS);
      } else {
        statement = connection.prepareStatement(querySpec.getSql());
      }
      try (PreparedStatement ignored = statement) {
        bindParameters(statement, querySpec.getParameters());
        int rowsAffected = statement.executeUpdate();
        if (rowsAffected <= 0) {
          markQueryFailure(querySpec);
          return Insert.NO_GENERATED_KEY;
        }
        if (returnGeneratedKeys) {
          try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
              long generatedId = generatedKeys.getLong(1);
              markQuerySuccess(querySpec);
              return generatedId;
            }
          }
          markQueryFailure(querySpec);
          return Insert.NO_GENERATED_KEY;
        }
        markQuerySuccess(querySpec);
        return rowsAffected;
      }
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("execute insert", querySpec, e);
      return Insert.NO_GENERATED_KEY;
    } finally {
      markExecutionTime(querySpec, startedNanos);
    }
  }

  /**
   * Executes a query and returns the first column from each row as an object list.
   *
   * @param querySpec the query to execute
   * @return every first-column value from the result set
   * @throws SQLException if execution fails
   */
  public static List<Object> executeList(QuerySpec querySpec) throws SQLException {
    Connection connection = getConnection();
    boolean closeConnection = connection != threadLocalConnection.get();
    try {
      return executeList(connection, querySpec);
    } finally {
      if (closeConnection) {
        connection.close();
      }
    }
  }

  /**
   * Executes the query against the given connection and returns each row's first column as an object list.
   *
   * @param connection the JDBC connection to use
   * @param querySpec the query to execute
   * @return each first-column value in list form
   * @throws SQLException if execution fails
   */
  public static List<Object> executeList(Connection connection, QuerySpec querySpec) throws SQLException {
    List<Object> rows = new ArrayList<>();
    long startedNanos = System.nanoTime();
    try {
      try (PreparedStatement statement = connection.prepareStatement(querySpec.getSql())) {
        bindParameters(statement, querySpec.getParameters());
        try (ResultSet resultSet = statement.executeQuery()) {
          while (resultSet.next()) {
            rows.add(resultSet.getObject(1));
          }
        }
      }
      applyPagingMetadata(connection, querySpec);
      markQuerySuccess(querySpec);
      return rows;
    } catch (SQLException e) {
      markQueryFailure(querySpec);
      logQueryException("execute list", querySpec, e);
      return rows;
    } finally {
      markExecutionTime(querySpec, startedNanos);
    }
  }

  /**
   * Populates the attached paging metadata with the total row count for a paginated SELECT.
   *
   * @param connection the connection used to execute the query
   * @param querySpec the query specification, if any
   * @throws SQLException if the count query cannot execute
   */
  private static void applyPagingMetadata(Connection connection, QuerySpec querySpec) throws SQLException {
    if (!(querySpec instanceof Select)) {
      return;
    }
    Select selectBuilder = (Select) querySpec;
    if (selectBuilder.getPaging() == null) {
      return;
    }
    String countSql = selectBuilder.getCountSql();
    if (countSql == null || countSql.isBlank()) {
      return;
    }
    String totalSql = "SELECT COUNT(*) FROM (" + countSql + ") AS total_count";
    try (PreparedStatement statement = connection.prepareStatement(totalSql)) {
      bindParameters(statement, querySpec.getParameters());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          selectBuilder.getPaging().setTotalCount(resultSet.getLong(1));
        }
      }
    }
  }

  /**
   * Determines whether the SQL text represents a SELECT-like query.
   *
   * @param sql the SQL to inspect
   * @return true when the statement starts with a SELECT-style keyword
   */
  private static boolean isSelectSql(String sql) {
    if (sql == null || sql.isBlank()) {
      return false;
    }
    String normalized = sql.trim().toUpperCase(Locale.ROOT);
    return normalized.startsWith("SELECT") || normalized.startsWith("WITH") || normalized.startsWith("SHOW")
        || normalized.startsWith("DESCRIBE");
  }

  /**
   * Determines whether the SQL text represents an INSERT statement.
   *
   * @param sql the SQL to inspect
   * @return true when the statement starts with INSERT
   */
  private static boolean isInsertSql(String sql) {
    if (sql == null || sql.isBlank()) {
      return false;
    }
    String normalized = sql.trim().toUpperCase(Locale.ROOT);
    return normalized.startsWith("INSERT");
  }

  /**
   * Binds every prepared-statement parameter using the query's ordered parameter list.
   *
   * @param statement the prepared statement to populate
   * @param parameters the parameter values in execution order
   * @throws SQLException if a parameter cannot be bound
   */
  private static void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
    for (int i = 0; i < parameters.size(); i++) {
      Object parameter = parameters.get(i);
      bindParameter(statement, i + 1, parameter);
    }
  }

  private static void bindParameter(PreparedStatement statement, int index, Object parameter) throws SQLException {
    if (parameter == null) {
      statement.setNull(index, Types.NULL);
      return;
    }

    if (parameter instanceof Field field) {
      bindFieldValue(statement, index, field);
      return;
    }

    if (parameter instanceof String[]) {
      bindArrayParameter(statement, index, "text", (String[]) parameter);
      return;
    }
    if (parameter instanceof Long[]) {
      bindArrayParameter(statement, index, "bigint", (Long[]) parameter);
      return;
    }
    if (parameter instanceof Integer[]) {
      bindArrayParameter(statement, index, "integer", (Integer[]) parameter);
      return;
    }
    if (parameter instanceof Double[]) {
      bindArrayParameter(statement, index, "double precision", (Double[]) parameter);
      return;
    }
    if (parameter instanceof Boolean[]) {
      bindArrayParameter(statement, index, "boolean", (Boolean[]) parameter);
      return;
    }
    if (parameter instanceof Timestamp[]) {
      bindArrayParameter(statement, index, "timestamp", (Timestamp[]) parameter);
      return;
    }
    if (parameter instanceof BigDecimal[]) {
      bindArrayParameter(statement, index, "numeric", (BigDecimal[]) parameter);
      return;
    }

    statement.setObject(index, parameter);
  }

  private static void bindFieldValue(PreparedStatement statement, int index, Field field) throws SQLException {
    if (field == null) {
      statement.setNull(index, Types.NULL);
      return;
    }

    if (field.isNull()) {
      statement.setNull(index, field.getSqlType() == Types.ARRAY ? Types.OTHER : field.getSqlType());
      return;
    }

    if (field.getCastType() == CastType.JSONB) {
      String jsonValue = field.getStringValue();
      if (jsonValue == null) {
        statement.setNull(index, Types.OTHER);
        return;
      }
      PGobject jsonb = new PGobject();
      jsonb.setType("jsonb");
      jsonb.setValue(jsonValue);
      statement.setObject(index, jsonb);
      return;
    }

    if (field.getSqlType() == Types.ARRAY) {
      if (field.getStringValues() != null) {
        bindArrayParameter(statement, index, "text", field.getStringValues());
      } else if (field.getLongValues() != null) {
        bindArrayParameter(statement, index, "bigint", field.getLongValues());
      } else if (field.getTimestampValues() != null) {
        bindArrayParameter(statement, index, "timestamp", field.getTimestampValues());
      } else if (field.getObjectValues() != null) {
        statement.setObject(index, field.getObjectValues());
      } else {
        statement.setNull(index, Types.OTHER);
      }
      return;
    }

    if (field.getSqlType() == Types.VARCHAR) {
      if (field.getStringValues() != null) {
        for (String value : field.getStringValues()) {
          statement.setString(index++, value);
        }
      } else {
        statement.setString(index, field.getStringValue());
      }
      return;
    }

    if (field.getSqlType() == Types.BIGINT) {
      if (field.getLongValues() != null) {
        for (Long value : field.getLongValues()) {
          statement.setLong(index++, value);
        }
      } else {
        statement.setLong(index, field.getLongValue());
      }
      return;
    }

    if (field.getSqlType() == Types.INTEGER) {
      statement.setInt(index, field.getIntValue());
      return;
    }

    if (field.getSqlType() == Types.DOUBLE) {
      statement.setDouble(index, field.getDoubleValue());
      return;
    }

    if (field.getSqlType() == Types.NUMERIC) {
      statement.setBigDecimal(index, field.getBigDecimalValue());
      return;
    }

    if (field.getSqlType() == Types.TIMESTAMP) {
      if (field.getTimestampValues() != null) {
        for (Timestamp value : field.getTimestampValues()) {
          statement.setTimestamp(index++, value);
        }
      } else {
        statement.setTimestamp(index, field.getTimestampValue());
      }
      return;
    }

    if (field.getSqlType() == Types.BOOLEAN) {
      statement.setBoolean(index, field.getBooleanValue());
      return;
    }

    if (field.getSqlType() == Types.JAVA_OBJECT) {
      if (field.getObjectValues() != null) {
        for (Object value : field.getObjectValues()) {
          if (value instanceof String) {
            statement.setString(index++, (String) value);
          } else if (value instanceof Integer) {
            statement.setInt(index++, (Integer) value);
          } else if (value instanceof Long) {
            statement.setLong(index++, (Long) value);
          } else if (value instanceof Double) {
            statement.setDouble(index++, (Double) value);
          } else if (value instanceof Boolean) {
            statement.setBoolean(index++, (Boolean) value);
          } else if (value instanceof Timestamp) {
            statement.setTimestamp(index++, (Timestamp) value);
          } else if (value instanceof BigDecimal) {
            statement.setBigDecimal(index++, (BigDecimal) value);
          } else {
            statement.setObject(index++, value);
          }
        }
      }
      return;
    }

    statement.setObject(index, field.getValue());
  }

  private static void bindArrayParameter(PreparedStatement statement, int index, String typeName, Object[] values)
      throws SQLException {
    if (values == null) {
      statement.setNull(index, Types.ARRAY);
      return;
    }
    try {
      statement.setArray(index, statement.getConnection().createArrayOf(typeName, values));
    } catch (SQLException e) {
      statement.setObject(index, values);
    }
  }

  /** Select the next value of a database sequence. */
  public static long selectNextSequenceValue(String sequenceName) {
    // Make sure sequence name is safe to use in SQL statement
    if (sequenceName == null || sequenceName.isBlank() || !sequenceName.matches("[a-zA-Z0-9_]+")) {
      return -1;
    }
    String sqlNextVal = "SELECT nextval('" + sequenceName + "')";
    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sqlNextVal);
        ResultSet resultSet = statement.executeQuery()) {
      if (resultSet.next()) {
        return resultSet.getLong(1);
      }
    } catch (SQLException e) {
      // best effort; return -1 when the sequence cannot be read
    }
    return -1;
  }

  /** Reset a database sequence to a specific value. */
  public static boolean resetSequence(String sequenceName, long value) {
    // Make sure sequence name is safe to use in SQL statement
    if (sequenceName == null || sequenceName.isBlank() || !sequenceName.matches("[a-zA-Z0-9_]+")) {
      return false;
    }
    String sqlRestartSequence = "ALTER SEQUENCE " + sequenceName + " RESTART WITH " + value;
    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sqlRestartSequence)) {
      statement.executeUpdate();
      return true;
    } catch (SQLException e) {
      // best effort; return false when the sequence cannot be reset
    }
    return false;
  }
}