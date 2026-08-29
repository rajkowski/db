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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Represents a SQL SELECT query builder, allowing the construction of SELECT statements
 * with various clauses such as FROM, JOIN, WHERE, ORDER BY, and more.
 */
@SuppressWarnings("java:S100")
public class Select extends QuerySpec {
  private final List<String> selectExpressions = new ArrayList<>();
  private String fromTable;
  private final List<String> joinClauses = new ArrayList<>();
  private final List<String> unionClauses = new ArrayList<>();
  private String pendingJoin;

  @Override
  public Select METRICS(QueryMetrics metrics) {
    super.METRICS(metrics);
    return this;
  }
  private final List<String> whereClauses = new ArrayList<>();
  private String orderByClause;
  private Paging paging;
  private DataConstraints dataConstraints;
  private String countSql;
  private Integer limit;
  private Integer offset;

  /**
   * Creates a SELECT builder that defaults to selecting all columns.
   */
  public Select() {
    this(new String[0]);
  }

  /**
   * Creates a SELECT builder for the specified column list.
   *
   * @param columns the columns to select; when empty, the builder selects all columns
   */
  public Select(String... columns) {
    if (columns == null || columns.length == 0) {
      selectExpressions.add("*");
      return;
    }
    SELECT(columns);
  }

  /**
   * Adds one or more columns to the select list.
   *
   * @param columns the columns to select; when empty, the builder selects all columns
   * @return this builder for chaining
   */
  public Select SELECT(String... columns) {
    if (columns == null || columns.length == 0) {
      if (selectExpressions.isEmpty()) {
        selectExpressions.add("*");
      }
      return this;
    }

    for (String column : columns) {
      String sanitized = sanitizeSelectExpression(column);
      if (sanitized.equals("*")) {
        selectExpressions.clear();
        selectExpressions.add("*");
        return this;
      }
      if (selectExpressions.size() == 1 && selectExpressions.get(0).equals("*")) {
        selectExpressions.clear();
      }
      selectExpressions.add(sanitized);
    }
    return this;
  }

  /**
   * Adds a parameterized SELECT expression to the select list.
   *
   * @param expression the SQL expression to include
   * @param values the parameter values bound to the expression
   * @return this builder for chaining
   */
  public Select SELECT(String expression, Object... values) {
    String sanitized = sanitizeSelectExpression(expression);
    if (values != null && values.length > 0) {
      for (Object value : values) {
        parameters.add(value);
      }
    }
    if (selectExpressions.size() == 1 && selectExpressions.get(0).equals("*")) {
      selectExpressions.clear();
    }
    selectExpressions.add(sanitized);
    return this;
  }

  /**
   * Adds the FROM clause to the query.
   *
   * @param table the table to select from
   * @return this builder for chaining
   */
  public Select FROM(String table) {
    this.fromTable = sanitizeIdentifier(table);
    return this;
  }

  /**
   * Adds the FROM clause to the query.
   *
   * @param table the table to select from
   * @return this builder for chaining
   */
  public Select FROM(Table table) {
    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null.");
    }
    return FROM(table.getName());
  }

  /**
   * Adds a derived-table FROM clause for a subquery.
   *
   * @param subquery the SELECT query used as the derived table
   * @return this builder for chaining
   */
  public Select FROM(QuerySpec subquery) {
    return FROM(subquery, (AS) null);
  }

  /**
   * Adds a derived-table FROM clause for a subquery with an alias.
   *
   * @param subquery the SELECT query used as the derived table
   * @param alias the alias for the derived table, if any
   * @return this builder for chaining
   */
  public Select FROM(QuerySpec subquery, String alias) {
    return FROM(subquery, alias == null ? null : new AS(alias));
  }

  /**
   * Adds a derived-table FROM clause for a subquery with an alias.
   *
   * @param subquery the SELECT query used as the derived table
   * @param alias the alias for the derived table, if any
   * @return this builder for chaining
   */
  public Select FROM(QuerySpec subquery, AS alias) {
    if (subquery == null) {
      throw new IllegalArgumentException("Subquery cannot be null.");
    }
    String derivedTable = "(" + subquery.getSql() + ")";
    if (alias != null) {
      derivedTable += " AS " + sanitizeDerivedTableAlias(alias.getAlias());
    }
    this.fromTable = derivedTable;
    this.parameters.addAll(subquery.getParameters());
    return this;
  }

  /**
   * Adds an alias to the current FROM table.
   *
   * @param alias the table alias to append
   * @return this builder for chaining
   */
  public Select AS(String alias) {
    return AS(new AS(alias));
  }

  /**
   * Adds an alias to the current FROM table.
   *
   * @param alias the table alias to append
   * @return this builder for chaining
   */
  public Select AS(AS alias) {
    if (alias == null) {
      return this;
    }
    if (this.fromTable == null || this.fromTable.isEmpty()) {
      throw new IllegalStateException("FROM(...) must be called before AS(...). ");
    }
    this.fromTable = this.fromTable + " AS " + sanitizeDerivedTableAlias(alias.getAlias());
    return this;
  }

  /**
   * Adds a UNION set operation to the query.
   *
   * @param otherQuery the query to combine with UNION
   * @return this builder for chaining
   */
  public Select UNION(QuerySpec otherQuery) {
    return addSetOperation("UNION", otherQuery);
  }

  /**
   * Adds a UNION ALL set operation to the query.
   *
   * @param otherQuery the query to combine with UNION ALL
   * @return this builder for chaining
   */
  public Select UNION_ALL(QuerySpec otherQuery) {
    return addSetOperation("UNION ALL", otherQuery);
  }

  /**
   * Adds a JOIN clause to the query.
   *
   * @param table the table or table alias to join with
   * @return this builder for chaining
   */
  public Select JOIN(String table) {
    String sanitized = sanitizeJoinTable(table);
    if (pendingJoin != null) {
      joinClauses.add(pendingJoin);
    }
    pendingJoin = "JOIN " + sanitized;
    return this;
  }

  /**
   * Adds a JOIN clause to the query.
   *
   * @param table the table to join with
   * @return this builder for chaining
   */
  public Select JOIN(Table table) {
    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null.");
    }
    return JOIN(table.getName());
  }

  /**
   * Adds a LEFT JOIN clause to the query.
   *
   * @param table the table or table alias to join with
   * @return this builder for chaining
   */
  public Select LEFT_JOIN(String table) {
    String sanitized = sanitizeJoinTable(table);
    if (pendingJoin != null) {
      joinClauses.add(pendingJoin);
    }
    pendingJoin = "LEFT JOIN " + sanitized;
    return this;
  }

  /**
   * Adds a LEFT JOIN clause to the query.
   *
   * @param table the table to join with
   * @return this builder for chaining
   */
  public Select LEFT_JOIN(Table table) {
    if (table == null) {
      throw new IllegalArgumentException("Table cannot be null.");
    }
    return LEFT_JOIN(table.getName());
  }

  /**
   * Adds the ON condition for the most recently added JOIN.
   *
   * @param clause the join condition such as x.id = y.id
   * @param values optional bound values for the join predicate
   * @return this builder for chaining
   */
  public Select ON(String clause, Object... values) {
    if (pendingJoin == null) {
      throw new IllegalStateException("JOIN must be called before ON(...). ");
    }
    String normalized = sanitizeJoinCondition(clause);
    pendingJoin = pendingJoin + " ON " + normalized;
    joinClauses.add(pendingJoin);
    pendingJoin = null;
    if (values != null) {
      for (Object value : values) {
        parameters.add(value);
      }
    }
    return this;
  }

  /**
   * Sets the select list to a COUNT aggregate expression.
   *
   * @param expression the expression to count
   * @return this builder for chaining
   */
  public Select COUNT(String expression) {
    String safeExpression = expression == null ? "*" : expression.trim();
    if (safeExpression.isEmpty()) {
      safeExpression = "*";
    }
    if (!safeExpression.matches("[A-Za-z0-9_.*]+")) {
      throw new IllegalArgumentException("COUNT expression contains unsupported characters.");
    }
    selectExpressions.clear();
    selectExpressions.add("COUNT(" + safeExpression + ")");
    return this;
  }

  /**
   * Starts a WHERE clause without adding a condition. This is useful when callers build
   * the query dynamically and want to begin with an empty filter state.
   *
   * @return this builder for chaining
   */
  public Select WHERE() {
    return this;
  }

  /**
   * Adds a WHERE condition with parameterized values to the query.
   *
   * @param clause the filter clause using ? placeholders
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public Select WHERE(String clause, Object... values) {
    if (clause == null || clause.trim().isEmpty()) {
      if (values == null || values.length == 0) {
        return this;
      }
      throw new IllegalArgumentException("Condition clause cannot be empty.");
    }
    addCondition(clause, values);
    return this;
  }

  /**
   * Appends an additional AND condition to the existing query.
   *
   * @param clause the filter clause using ? placeholders
   * @param values the values bound to the clause
   * @return this builder for chaining
   */
  public Select AND(String clause, Object... values) {
    addCondition(clause, values);
    return this;
  }

  /**
   * Adds a WHERE clause only when the supplied value is not null.
   * 
   * @param clause the filter clause using ? placeholders
   * @param value the value to check for null
   * @return this builder for chaining
   */
  public Select WHERE_SKIP_IF_NULL(String clause, Object value) {
    return WHERE_SKIP_IF_MATCHES(clause, value, null);
  }

  /**
   * Adds a WHERE clause only when the supplied value does not match the caller's skip value.
   *
   * @param clause the filter clause using ? placeholders
   * @param value the current runtime value to evaluate
   * @param comparisonValue the value that should suppress the clause when matched
   * @return this builder for chaining
   */
  public Select WHERE_SKIP_IF_MATCHES(String clause, Object value, Object comparisonValue) {
    if (shouldSkipCondition(value, comparisonValue)) {
      return this;
    }
    return WHERE(clause, value);
  }

  /**
   * Appends an additional AND condition to the existing query only when the supplied value does not match the caller's skip value.
   * 
   * @param clause the filter clause using ? placeholders
   * @param value the current runtime value to evaluate
   * @return this builder for chaining
   */
  public Select AND_SKIP_IF_NULL(String clause, Object value) {
    return AND_SKIP_IF_MATCHES(clause, value, null);
  }

  /**
   * Appends an AND clause only when the supplied value does not match the caller's comparison value.
   *
   * @param clause the filter clause using ? placeholders
   * @param value the current runtime value to evaluate
   * @param comparisonValue the value that should suppress the clause when matched
   * @return this builder for chaining
   */
  public Select AND_SKIP_IF_MATCHES(String clause, Object value, Object comparisonValue) {
    if (shouldSkipCondition(value, comparisonValue)) {
      return this;
    }
    return AND(clause, value);
  }

  /**
   * Applies sorting and paging values from the supplied DataConstraints object.
   *
   * @param constraints the request metadata including sorting and paging values
   * @return this builder for chaining
   */
  public Select WITH(DataConstraints constraints) {
    if (constraints == null) {
      return this;
    }
    return ORDER_BY(constraints).PAGING(constraints);
  }

  /**
   * Adds an ORDER BY clause to the SELECT query.
   *
   * @param clause the ordering expression to apply
   * @return this builder for chaining
   */
  public Select ORDER_BY(String clause) {
    this.orderByClause = sanitizeOrderBy(clause);
    return this;
  }

  /**
   * Applies a sort specification from the supplied constraints object.
   *
   * @param constraints the sorting and paging metadata to apply
   * @return this builder for chaining
   */
  public Select ORDER_BY(DataConstraints constraints) {
    if (constraints == null) {
      return this;
    }
    this.dataConstraints = constraints;
    String orderBy = buildOrderByClause(constraints);
    if (orderBy != null && !orderBy.isEmpty()) {
      return ORDER_BY(orderBy);
    }
    return this;
  }

  /**
   * Adds a LIMIT clause to the SELECT query.
   *
   * @param limit the maximum number of rows to return
   * @return this builder for chaining
   */
  public Select LIMIT(int limit) {
    if (limit < 0) {
      throw new IllegalArgumentException("LIMIT must be zero or greater.");
    }
    if (countSql == null) {
      countSql = buildSql(false);
    }
    if (this.limit != null) {
      return this;
    }
    this.limit = limit;
    return this;
  }

  /**
   * Adds an OFFSET clause to the SELECT query.
   *
   * @param offset the number of rows to skip before returning results
   * @return this builder for chaining
   */
  public Select OFFSET(int offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("OFFSET must be zero or greater.");
    }
    if (countSql == null) {
      countSql = buildSql(false);
    }
    if (this.offset != null) {
      return this;
    }
    this.offset = offset;
    return this;
  }

  /**
   * Applies paging values from the supplied object to the query and stores the paging metadata target.
   *
   * @param paging the pagination request and result carrier
   * @return this builder for chaining
   */
  public Select PAGING(Paging paging) {
    if (paging == null) {
      throw new IllegalArgumentException("Paging cannot be null.");
    }
    this.paging = paging;
    if (this.limit == null) {
      LIMIT(paging.getPageSize());
    }
    if (this.offset == null) {
      OFFSET(paging.getOffset());
    }
    return this;
  }

  /**
   * Applies paging values from the supplied DataConstraints object.
   *
   * @param constraints the request metadata including paging values
   * @return this builder for chaining
   */
  public Select PAGING(DataConstraints constraints) {
    if (constraints == null) {
      return this;
    }
    this.dataConstraints = constraints;
    if (constraints.getPageSize() > 0) {
      this.paging = constraints;
      if (this.limit == null) {
        LIMIT(constraints.getPageSize());
      }
      if (this.offset == null) {
        OFFSET(constraints.getOffset());
      }
    } else {
      this.paging = null;
    }
    return this;
  }

  /**
   * Returns the unpaginated SQL used to calculate total row counts while paging.
   *
   * @return the SQL before any LIMIT/OFFSET clauses were appended
   */
  public String getCountSql() {
    return countSql == null ? buildSql(false) : countSql.trim();
  }

  /**
   * Returns the paging metadata object associated with this select.
   *
   * @return the current paging configuration, or null if no paging metadata is attached
   */
  public Paging getPaging() {
    return paging;
  }

  /**
   * Returns the attached DataConstraints metadata, when present.
   *
   * @return the active constraint object or null
   */
  public DataConstraints getDataConstraints() {
    return dataConstraints;
  }

  @Override
  public String getSql() {
    return buildSql(true).trim();
  }

  private String buildOrderByClause(DataConstraints constraints) {
    if (constraints == null) {
      return null;
    }
    String[] columns = constraints.getColumnsToSortBy();
    if (columns == null || columns.length == 0) {
      String defaultColumn = constraints.getDefaultColumnToSortBy();
      if (defaultColumn == null || defaultColumn.trim().isEmpty()) {
        return null;
      }
      columns = new String[] { defaultColumn };
    }
    String[] ordering = constraints.getSortOrder();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < columns.length; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      String column = sanitizeOrderBy(columns[i]);
      builder.append(column);
      if (ordering != null && i < ordering.length && ordering[i] != null && !ordering[i].trim().isEmpty()) {
        String direction = ordering[i].trim();
        if ("ASC".equalsIgnoreCase(direction) || "DESC".equalsIgnoreCase(direction)) {
          builder.append(' ').append(direction.toUpperCase(Locale.ROOT));
        }
      }
    }
    return builder.length() == 0 ? null : builder.toString();
  }

  private String sanitizeJoinTable(String table) {
    if (table == null || table.trim().isEmpty()) {
      throw new IllegalArgumentException("JOIN table cannot be empty.");
    }
    String trimmed = table.trim();
    if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
      throw new IllegalArgumentException("Unsafe JOIN table detected.");
    }
    if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_\\.]*?(?:\\s+(?:AS\\s+)?[A-Za-z_][A-Za-z0-9_]*)?")) {
      throw new IllegalArgumentException("Invalid JOIN table: " + table);
    }
    return trimmed;
  }

  private String sanitizeDerivedTableAlias(String alias) {
    if (alias == null || alias.trim().isEmpty()) {
      throw new IllegalArgumentException("Derived table alias cannot be empty.");
    }
    String trimmed = alias.trim();
    if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
      throw new IllegalArgumentException("Unsafe derived table alias detected.");
    }
    if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("Invalid derived table alias: " + alias);
    }
    return trimmed;
  }

  private Select addSetOperation(String operator, QuerySpec otherQuery) {
    if (otherQuery == null) {
      throw new IllegalArgumentException("Query cannot be null.");
    }
    unionClauses.add(operator + " " + otherQuery.getSql());
    this.parameters.addAll(otherQuery.getParameters());
    return this;
  }

  private String sanitizeJoinCondition(String clause) {
    if (clause == null || clause.trim().isEmpty()) {
      throw new IllegalArgumentException("JOIN condition cannot be empty.");
    }
    String trimmed = clause.trim();
    if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
      throw new IllegalArgumentException("Unsafe JOIN condition detected.");
    }
    if (!trimmed.matches("[A-Za-z0-9_\\.\\s=<>!()?@%:,\\[\\]\\+\\-\\*\\|&'\\\"$\\/{}/\\\\]+")) {
      throw new IllegalArgumentException("Unsafe JOIN condition detected.");
    }
    return trimmed;
  }

  private boolean shouldSkipCondition(Object value, Object skipValue) {
    if (skipValue == null) {
      return value == null;
    }
    if (value == null) {
      return false;
    }
    return skipValue.equals(value);
  }

  private void addCondition(String clause, Object... values) {
    String normalized = sanitizeConditionClause(clause);
    if (!whereClauses.isEmpty()) {
      String previous = whereClauses.get(whereClauses.size() - 1);
      if (previous.equals(normalized)) {
        return;
      }
    }
    whereClauses.add(normalized);
    if (values != null) {
      for (Object value : values) {
        parameters.add(value);
      }
    }
  }

  private String buildSql(boolean includePaging) {
    StringBuilder builder = new StringBuilder();
    if (selectExpressions.isEmpty()) {
      builder.append("SELECT *");
    } else {
      builder.append("SELECT ").append(String.join(", ", selectExpressions));
    }

    if (fromTable != null && !fromTable.isEmpty()) {
      builder.append(" FROM ").append(fromTable);
    }

    if (!joinClauses.isEmpty()) {
      for (String joinClause : joinClauses) {
        builder.append(' ').append(joinClause);
      }
    }
    if (pendingJoin != null && !pendingJoin.isBlank()) {
      builder.append(' ').append(pendingJoin);
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

    if (!unionClauses.isEmpty()) {
      for (String unionClause : unionClauses) {
        builder.append(' ').append(unionClause);
      }
    }

    if (orderByClause != null && !orderByClause.isEmpty()) {
      builder.append(" ORDER BY ").append(orderByClause);
    }

    if (includePaging && limit != null) {
      builder.append(" LIMIT ").append(limit);
    }
    if (includePaging && offset != null) {
      builder.append(" OFFSET ").append(offset);
    }

    return builder.toString().trim();
  }
}
