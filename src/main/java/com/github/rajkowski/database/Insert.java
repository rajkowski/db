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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a SQL INSERT query builder, allowing the construction of INSERT statements
 * with column/value lists, conflict handling, and support for bound parameters and execution metrics.
 */
@SuppressWarnings("java:S100")
public class Insert extends QuerySpec {
  public static final long NO_GENERATED_KEY = -1L;

  private static final class RawField {
    private final String columnName;
    private final String expression;

    private RawField(String columnName, String expression) {
      this.columnName = columnName;
      this.expression = expression;
    }
  }

  private String tableName;
  private final List<Object> insertEntries = new ArrayList<>();
  private final List<String> conflictColumns = new ArrayList<>();
  private final List<String> updateAssignments = new ArrayList<>();
  private String conflictWhereClause;
  private final List<Object> conflictWhereValues = new ArrayList<>();
  private boolean doUpdate;
  private boolean returnGeneratedKeys = true;

  @Override
  public Insert METRICS(QueryMetrics metrics) {
    super.METRICS(metrics);
    return this;
  }

  /**
   * Creates an INSERT statement builder.
   */
  public Insert() {
  }

  /**
   * Adds the INTO table clause to the insert statement.
   *
   * @param table the table to insert into
   * @return this builder for chaining
   */
  public Insert INTO(String table) {
    this.tableName = sanitizeIdentifier(table);
    return this;
  }

  /**
   * Adds the INTO table clause to the insert statement.
   *
   * @param table the table to insert into
   * @return this builder for chaining
   */
  public Insert INTO(Table table) {
    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null.");
    }
    return INTO(table.getName());
  }

  /**
   * Adds a column/value list to the insert statement using the provided Field objects.
   *
   * @param fields the fields to insert
   * @return this builder for chaining
   */
  public Insert FIELDS(Field... fields) {
    if (fields == null || fields.length == 0) {
      return this;
    }
    for (Field field : fields) {
      if (field == null) {
        continue;
      }
      insertEntries.add(field);
      if (field.hasValue()) {
        parameters.add(readFieldValue(field));
      }
    }
    return this;
  }

  /**
   * Adds a raw SQL expression to the VALUES list, such as "locked_at = CURRENT_TIMESTAMP".
   *
   * @param expression the expression in the form "column = SQL expression"
   * @return this builder for chaining
   */
  public Insert FIELD(String expression) {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("Insert field expression cannot be empty.");
    }
    String normalized = sanitizeConditionClause(expression);
    int equalsIndex = normalized.indexOf('=');
    if (equalsIndex < 0) {
      throw new IllegalArgumentException("Insert field expression must be in the form 'column = expression'.");
    }
    String columnName = normalized.substring(0, equalsIndex).trim();
    String rawExpression = normalized.substring(equalsIndex + 1).trim();
    if (columnName.isEmpty() || rawExpression.isEmpty()) {
      throw new IllegalArgumentException("Insert field expression must include both a column name and value expression.");
    }
    insertEntries.add(new RawField(sanitizeIdentifier(columnName), rawExpression));
    return this;
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, String value) {
    return FIELDS(new Field(name, value));
  }

  /**
   * Adds a field to the insert list with a database-specific cast type.
   *
   * @param name the column name
   * @param value the value to bind as a casted SQL string
   * @param castType the database cast type, such as CastType.JSONB
   * @return this builder for chaining
   */
  public Insert FIELD(String name, String value, CastType castType) {
    return FIELDS(new Field(name, value, castType));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param values the column value array
   * @return this builder for chaining
   */
  public Insert FIELD(String name, String[] values) {
    return FIELDS(new Field(name, values));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, long value) {
    return FIELDS(new Field(name, value));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, Long value) {
    return value == null ? FIELD(name, (String) null) : FIELDS(new Field(name, value.longValue()));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, int value) {
    return FIELDS(new Field(name, value));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, Integer value) {
    return value == null ? FIELD(name, (String) null) : FIELDS(new Field(name, value.intValue()));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, double value) {
    return FIELDS(new Field(name, value));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, Double value) {
    return value == null ? FIELD(name, (String) null) : FIELDS(new Field(name, value.doubleValue()));
  }

  /**
   * Adds a PostGIS point field using latitude and longitude coordinates.
   *
   * @param name the geometry column name
   * @param latitude the point latitude
   * @param longitude the point longitude
   * @return this builder for chaining
   */
  public Insert POINT(String name, double latitude, double longitude) {
    return FIELDS(new Field(name, latitude, longitude, CastType.GEOM));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, boolean value) {
    return FIELDS(new Field(name, value));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, Boolean value) {
    return value == null ? FIELD(name, (String) null) : FIELDS(new Field(name, value.booleanValue()));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, Timestamp value) {
    return FIELDS(new Field(name, value));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, BigDecimal value) {
    return FIELDS(new Field(name, value));
  }

  /**
   * Adds a field to the insert list using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Insert FIELD(String name, Object value) {
    if (value instanceof String) {
      return FIELD(name, (String) value);
    }
    if (value instanceof String[]) {
      return FIELD(name, (String[]) value);
    }
    if (value instanceof Long) {
      return FIELD(name, (Long) value);
    }
    if (value instanceof Integer) {
      return FIELD(name, (Integer) value);
    }
    if (value instanceof Double) {
      return FIELD(name, (Double) value);
    }
    if (value instanceof Boolean) {
      return FIELD(name, (Boolean) value);
    }
    if (value instanceof Timestamp) {
      return FIELD(name, (Timestamp) value);
    }
    if (value instanceof BigDecimal) {
      return FIELD(name, (BigDecimal) value);
    }
    if (value == null) {
      return FIELD(name, (String) null);
    }
    throw new IllegalArgumentException("Unsupported field value type: " + value.getClass().getName());
  }

  public Insert FIELD_UNLESS_NULL(String name, Object value) {
    if (value == null) {
      return this;
    }
    return FIELD(name, value);
  }

  public Insert FIELD_UNLESS_MATCHES(String name, Object value, Object nullComparisonValue) {
    if (value == null || (nullComparisonValue != null && value.equals(nullComparisonValue))) {
      return this;
    }
    return FIELD(name, value);
  }

  /**
   * Executes the insert and returns the generated key value.
   *
   * @return the generated primary key value or NO_GENERATED_KEY when none is available
   */
  /**
   * Sets the conflict target for an INSERT ... ON CONFLICT clause.
   *
   * @param columns one or more unique index columns to match on
   * @return this builder for chaining
   */
  public Insert ON_CONFLICT(String... columns) {
    if (columns == null || columns.length == 0) {
      throw new IllegalArgumentException("Conflict columns cannot be empty.");
    }
    conflictColumns.clear();
    for (String column : columns) {
      if (column == null || column.isBlank()) {
        continue;
      }
      conflictColumns.add(sanitizeIdentifier(column.trim()));
    }
    if (conflictColumns.isEmpty()) {
      throw new IllegalArgumentException("Conflict columns cannot be empty.");
    }
    return this;
  }

  /**
   * Marks this insert for an UPDATE-on-conflict branch.
   *
   * @return this builder for chaining
   */
  public Insert DO_UPDATE() {
    if (conflictColumns.isEmpty()) {
      throw new IllegalStateException("ON_CONFLICT(...) must be set before DO_UPDATE().");
    }
    doUpdate = true;
    returnGeneratedKeys = false;
    return this;
  }

  /**
   * Indicates whether the insert expects JDBC-generated keys to be returned.
   *
   * @return true when the SQL should request generated keys, false for PostgreSQL-style UPSERTs
   */
  public boolean isReturnGeneratedKeys() {
    return returnGeneratedKeys;
  }

  /**
   * Adds a raw SQL assignment for the DO UPDATE branch.
   *
   * @param assignment the assignment clause such as "name = EXCLUDED.name"
   * @return this builder for chaining
   */
  public Insert SET(String assignment) {
    if (assignment == null || assignment.isBlank()) {
      throw new IllegalArgumentException("Assignment cannot be empty.");
    }
    if (!doUpdate) {
      throw new IllegalStateException("SET(...) is only valid after DO_UPDATE().");
    }
    String normalized = sanitizeConditionClause(assignment);
    updateAssignments.add(normalized);
    return this;
  }

  /**
   * Adds a WHERE clause to the DO UPDATE branch.
   *
   * @param clause the filter clause using ? placeholders when needed
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public Insert WHERE(String clause, Object... values) {
    if (!doUpdate) {
      throw new IllegalStateException("WHERE(...) is only valid after DO_UPDATE().");
    }
    String normalized = sanitizeConditionClause(clause);
    conflictWhereClause = normalized;
    conflictWhereValues.clear();
    if (values != null) {
      for (Object value : values) {
        conflictWhereValues.add(value);
      }
    }
    return this;
  }

  /**
   * Adds an additional AND condition to the conflict WHERE clause.
   *
   * @param clause the filter clause using ? placeholders when needed
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public Insert AND(String clause, Object... values) {
    if (conflictWhereClause == null || conflictWhereClause.isBlank()) {
      return WHERE(clause, values);
    }
    String normalized = sanitizeConditionClause(clause);
    conflictWhereClause = conflictWhereClause + " AND " + normalized;
    if (values != null) {
      for (Object value : values) {
        conflictWhereValues.add(value);
      }
    }
    return this;
  }

  @Override
  public Long execute() {
    return DB.executeInsert(this);
  }

  /**
   * Executes the insert on the supplied connection and returns the generated key value.
   *
   * @param connection the JDBC connection to use
   * @return the generated primary key value or NO_GENERATED_KEY when none is available
   */
  @Override
  public Long execute(Connection connection) {
    return DB.executeInsert(connection, this);
  }

  @Override
  public java.util.List<Object> getParameters() {
    java.util.ArrayList<Object> all = new java.util.ArrayList<>(parameters);
    if (doUpdate && !conflictWhereValues.isEmpty()) {
      all.addAll(conflictWhereValues);
    }
    return java.util.Collections.unmodifiableList(all);
  }

  @Override
  public String getSql() {
    StringBuilder builder = new StringBuilder("INSERT");
    if (tableName != null && !tableName.isEmpty()) {
      builder.append(" INTO ").append(tableName);
    }
    if (!insertEntries.isEmpty()) {
      StringBuilder columnNames = new StringBuilder();
      StringBuilder values = new StringBuilder();
      for (int i = 0; i < insertEntries.size(); i++) {
        Object entry = insertEntries.get(i);
        if (entry == null) {
          continue;
        }
        if (columnNames.length() > 0) {
          columnNames.append(", ");
          values.append(", ");
        }
        if (entry instanceof Field) {
          Field field = (Field) entry;
          columnNames.append(sanitizeIdentifier(field.getName()));
          values.append(field.hasValue() ? "?" : field.getValue());
        } else if (entry instanceof RawField) {
          RawField rawField = (RawField) entry;
          columnNames.append(rawField.columnName);
          values.append(rawField.expression);
        }
      }
      builder.append(" (")
          .append(columnNames)
          .append(") VALUES (")
          .append(values)
          .append(")");
    }
    if (!conflictColumns.isEmpty() && doUpdate) {
      builder.append(" ON CONFLICT (")
          .append(String.join(", ", conflictColumns))
          .append(") DO UPDATE SET ");
      for (int i = 0; i < updateAssignments.size(); i++) {
        if (i > 0) {
          builder.append(", ");
        }
        builder.append(updateAssignments.get(i));
      }
      if (conflictWhereClause != null && !conflictWhereClause.isBlank()) {
        builder.append(" WHERE ").append(conflictWhereClause);
      }
    }
    return builder.toString().trim();
  }
}
