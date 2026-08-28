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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a SQL UPDATE query builder, allowing the construction of UPDATE statements
 * with SET and WHERE clauses, and support for bound parameters and execution metrics.
 */
@SuppressWarnings("java:S100")
public class Update extends QuerySpec {
  private String tableName;
  private final List<Field> setFields = new ArrayList<>();
  private final List<String> rawSetClauses = new ArrayList<>();

  @Override
  public Update METRICS(QueryMetrics metrics) {
    super.METRICS(metrics);
    return this;
  }
  private final List<String> whereClauses = new ArrayList<>();
  private final List<Object> setValues = new ArrayList<>();
  private final List<Object> whereValues = new ArrayList<>();

  /**
   * Creates an UPDATE statement for a given table.
   *
   * @param table the table to update
   */
  public Update(String table) {
    this.tableName = sanitizeIdentifier(table);
  }

  /**
   * Creates an UPDATE statement for a given table.
   *
   * @param table the table to update
   */
  public Update(Table table) {
    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null.");
    }
    this.tableName = table.getName();
  }

  /**
   * Adds a SET clause to update one or more fields with bound parameter values.
   *
   * @param fields the fields to assign in the update statement
   * @return this builder for chaining
   */
  public Update SET(Field... fields) {
    if (fields == null || fields.length == 0) {
      return this;
    }
    for (Field field : fields) {
      if (field == null) {
        continue;
      }
      setFields.add(field);
      setValues.add(readFieldValue(field));
    }
    return this;
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, String value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair and a cast type.
   *
   * @param name the column name
   * @param value the string value to bind using the supplied cast
   * @param castType the database cast type, such as CastType.JSONB
   * @return this builder for chaining
   */
  public Update SET(String name, String value, CastType castType) {
    return SET(new Field(name, value, castType));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param values the column value array
   * @return this builder for chaining
   */
  public Update SET(String name, String[] values) {
    return SET(new Field(name, values));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, long value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, Long value) {
    return value == null ? SET(name, (String) null) : SET(new Field(name, value.longValue()));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, int value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, Integer value) {
    return value == null ? SET(name, (String) null) : SET(new Field(name, value.intValue()));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, double value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, Double value) {
    return value == null ? SET(name, (String) null) : SET(new Field(name, value.doubleValue()));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, boolean value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, Boolean value) {
    return value == null ? SET(name, (String) null) : SET(new Field(name, value.booleanValue()));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, Timestamp value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, BigDecimal value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update SET(String name, Object value) {
    if (value instanceof String) {
      return SET(name, (String) value);
    }
    if (value instanceof String[]) {
      return SET(name, (String[]) value);
    }
    if (value instanceof Long) {
      return SET(name, (Long) value);
    }
    if (value instanceof Integer) {
      return SET(name, (Integer) value);
    }
    if (value instanceof Double) {
      return SET(name, (Double) value);
    }
    if (value instanceof Boolean) {
      return SET(name, (Boolean) value);
    }
    if (value instanceof Timestamp) {
      return SET(name, (Timestamp) value);
    }
    if (value instanceof BigDecimal) {
      return SET(name, (BigDecimal) value);
    }
    if (value == null) {
      return SET(name, (String) null);
    }
    throw new IllegalArgumentException("Unsupported field value type: " + value.getClass().getName());
  }

  /**
   * Adds a raw SQL assignment expression to the update statement.
   *
   * @param assignment the assignment clause such as "page_xml = draft_page_xml"
   * @return this builder for chaining
   */
  public Update SET(String assignment) {
    if (assignment == null || assignment.isBlank()) {
      throw new IllegalArgumentException("Assignment cannot be empty.");
    }
    String normalized = sanitizeConditionClause(assignment);
    rawSetClauses.add(normalized);
    return this;
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, String value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param values the column value array
   * @return this builder for chaining
   */
  public Update add(String name, String[] values) {
    return SET(new Field(name, values));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, long value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, Long value) {
    return value == null ? add(name, (String) null) : SET(new Field(name, value.longValue()));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, int value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, Integer value) {
    return value == null ? add(name, (String) null) : SET(new Field(name, value.intValue()));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, double value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, Double value) {
    return value == null ? add(name, (String) null) : SET(new Field(name, value.doubleValue()));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, boolean value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, Boolean value) {
    return value == null ? add(name, (String) null) : SET(new Field(name, value.booleanValue()));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, Timestamp value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, BigDecimal value) {
    return SET(new Field(name, value));
  }

  /**
   * Adds a field assignment using a name/value pair.
   *
   * @param name the column name
   * @param value the column value
   * @return this builder for chaining
   */
  public Update add(String name, Object value) {
    if (value instanceof String) {
      return add(name, (String) value);
    }
    if (value instanceof String[]) {
      return add(name, (String[]) value);
    }
    if (value instanceof Long) {
      return add(name, (Long) value);
    }
    if (value instanceof Integer) {
      return add(name, (Integer) value);
    }
    if (value instanceof Double) {
      return add(name, (Double) value);
    }
    if (value instanceof Boolean) {
      return add(name, (Boolean) value);
    }
    if (value instanceof Timestamp) {
      return add(name, (Timestamp) value);
    }
    if (value instanceof BigDecimal) {
      return add(name, (BigDecimal) value);
    }
    if (value == null) {
      return add(name, (String) null);
    }
    throw new IllegalArgumentException("Unsupported field value type: " + value.getClass().getName());
  }

  /**
   * Adds a WHERE condition to the update statement.
   *
   * @param clause the filter clause using ? placeholders
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public Update WHERE(String clause, Object... values) {
    String normalized = sanitizeConditionClause(clause);
    whereClauses.add(normalized);
    if (values != null) {
      for (Object value : values) {
        whereValues.add(value);
      }
    }
    return this;
  }

  /**
   * Appends an additional AND condition to the existing update predicate.
   *
   * @param clause the filter clause using ? placeholders
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public Update AND(String clause, Object... values) {
    return WHERE(clause, values);
  }

  @Override
  public Boolean execute() {
    try {
      int rowsAffected = DB.executeUpdate(this);
      return rowsAffected > 0;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to execute update: " + e.getMessage(), e);
    }
  }

  @Override
  public Boolean execute(Connection connection) throws SQLException {
    int rowsAffected = DB.executeUpdate(connection, this);
    return rowsAffected > 0;
  }

  @Override
  public java.util.List<Object> getParameters() {
    java.util.ArrayList<Object> all = new java.util.ArrayList<>();
    all.addAll(setValues);
    all.addAll(whereValues);
    return java.util.Collections.unmodifiableList(all);
  }

  @Override
  public String getSql() {
    StringBuilder builder = new StringBuilder("UPDATE ").append(tableName);
    if (!setFields.isEmpty() || !rawSetClauses.isEmpty()) {
      StringBuilder assignments = new StringBuilder();
      for (int i = 0; i < setFields.size(); i++) {
        if (assignments.length() > 0) {
          assignments.append(", ");
        }
        Field field = setFields.get(i);
        if (field != null) {
          assignments.append(sanitizeIdentifier(field.getName())).append(" = ?");
        }
      }
      for (int i = 0; i < rawSetClauses.size(); i++) {
        if (assignments.length() > 0) {
          assignments.append(", ");
        }
        assignments.append(rawSetClauses.get(i));
      }
      builder.append(" SET ").append(assignments);
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
