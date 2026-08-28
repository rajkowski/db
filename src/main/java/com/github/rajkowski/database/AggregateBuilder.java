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

public class AggregateBuilder extends QuerySpec {

  /**
   * Creates a SELECT aggregate query such as COUNT or SUM.
   *
   * @param aggregateName the aggregation function name
   * @param expression the expression being aggregated
   */
  public AggregateBuilder(String aggregateName, String expression) {
    String safeExpression = expression == null ? "*" : expression.trim();
    if (safeExpression.isEmpty()) {
      safeExpression = "*";
    }
    if (!safeExpression.matches("[A-Za-z0-9_.*]+")) {
      throw new IllegalArgumentException("Aggregate expression contains unsupported characters.");
    }
    sql.append("SELECT ").append(aggregateName.toUpperCase()).append("(")
        .append(safeExpression)
        .append(")");
  }

  /**
   * Adds a FROM clause to the aggregate query.
   *
   * @param table the table to aggregate over
   * @return this builder for chaining
   */
  public AggregateBuilder FROM(String table) {
    appendSqlFragment("FROM");
    appendSqlFragment(sanitizeIdentifier(table));
    return this;
  }

  /**
   * Adds a FROM clause to the aggregate query.
   *
   * @param table the table to aggregate over
   * @return this builder for chaining
   */
  public AggregateBuilder FROM(Table table) {
    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null.");
    }
    return FROM(table.getName());
  }

  /**
   * Adds a WHERE condition to the aggregate query.
   *
   * @param clause the filter clause using ? placeholders
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public AggregateBuilder WHERE(String clause, Object... values) {
    if (clause == null || clause.isEmpty()) {
      throw new IllegalArgumentException("WHERE clause cannot be empty.");
    }
    appendCondition("WHERE", clause, values);
    return this;
  }
}
