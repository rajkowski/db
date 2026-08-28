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

import java.util.Locale;

/**
 * Represents a group of SQL conditions, typically used in WHERE clauses,
 * with support for logical operators and JSONB casting.
 */
public record ConditionGroup(String sql, String[] values) {

  public static final String ALL = "AND"; // All of these
  public static final String ANY = "OR"; // Any of these
  public static final String NONE = "NOT AND"; // None of these
  public static final String NOT_ANY = "NOT OR"; // Not any of these

  public static ConditionGroup build(String columnName, String[] tags, String operator) {
    return build(columnName, tags, operator, CastType.JSONB);
  }

  public static ConditionGroup build(String columnName, String[] tags, String operator, CastType castType) {
    if (columnName == null || columnName.isBlank() || tags == null || tags.length == 0) {
      return null;
    }

    String normalizedOperator = (operator == null) ? ALL : operator.trim().toUpperCase(Locale.ROOT);
    String logicalOperator = (ANY.equals(normalizedOperator) || NOT_ANY.equals(normalizedOperator)) ? ANY : ALL;
    StringBuilder sql = new StringBuilder();
    if (NONE.equals(normalizedOperator) || NOT_ANY.equals(normalizedOperator)) {
      sql.append("NOT ");
    }

    String castSuffix = buildCastSuffix(castType);
    sql.append("(");
    String[] values = new String[tags.length];
    for (int i = 0; i < tags.length; i++) {
      if (i > 0) {
        sql.append(" ").append(logicalOperator).append(" ");
      }
      sql.append(columnName).append(" @> ?").append(castSuffix);
      values[i] = "[\"" + escapeJsonString(tags[i]) + "\"]";
    }
    sql.append(")");
    return new ConditionGroup(sql.toString(), values);
  }

  private static String buildCastSuffix(CastType castType) {
    if (castType == null || CastType.AS_IS.equals(castType)) {
      return "";
    }
    String resolvedType = castType.value();
    if (resolvedType == null || resolvedType.isBlank()) {
      return "::jsonb";
    }
    return "::" + resolvedType.trim().toLowerCase(Locale.ROOT);
  }

  private static String escapeJsonString(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\"':
          escaped.append("\\\"");
          break;
        case '\\':
          escaped.append("\\\\");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\t':
          escaped.append("\\t");
          break;
        default:
          if (c < 0x20) {
            escaped.append(String.format("\\u%04x", (int) c));
          } else {
            escaped.append(c);
          }
      }
    }
    return escaped.toString();
  }
}
