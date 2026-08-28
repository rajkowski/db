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
import java.sql.Timestamp;
import java.sql.Types;

import org.apache.commons.lang3.StringUtils;

/**
 * Represents a database field used in insert/update statements and where clause
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class Field {

  private String name;
  private int sqlType;
  private CastType castType = null;
  private boolean isNull = false;
  private boolean hasValue = true;

  private Object value;

  public Field(String name) {
    this.name = name;
    this.hasValue = false;
  }

  public Field(String name, String stringValue) {
    this.name = name;
    this.value = stringValue;
    this.sqlType = Types.VARCHAR;
  }

  public Field(String name, String[] stringValues) {
    this.name = name;
    this.value = stringValues;
    this.sqlType = Types.ARRAY;
  }

  public Field(String name, String[] stringValues, int sqlType) {
    this.name = name;
    this.value = stringValues;
    this.sqlType = sqlType;
  }

  public Field(String name, long longValue) {
    this.name = name;
    this.value = longValue;
    this.sqlType = Types.BIGINT;
  }

  public Field(String name, Long[] longValues) {
    this.name = name;
    this.value = longValues;
    this.sqlType = Types.ARRAY;
  }

  public Field(String name, long longValue, boolean isNull) {
    this.name = name;
    this.value = longValue;
    this.sqlType = Types.BIGINT;
    this.isNull = isNull;
  }

  public Field(String name, int intValue) {
    this.name = name;
    this.value = intValue;
    this.sqlType = Types.INTEGER;
  }

  public Field(String name, int intValue, boolean isNull) {
    this.name = name;
    this.value = intValue;
    this.sqlType = Types.INTEGER;
    this.isNull = isNull;
  }

  public Field(String name, double doubleValue) {
    this.name = name;
    this.value = doubleValue;
    this.sqlType = Types.DOUBLE;
  }

  public Field(String name, double doubleValue, boolean isNull) {
    this.name = name;
    this.value = doubleValue;
    this.sqlType = Types.DOUBLE;
    this.isNull = isNull;
  }

  public Field(String name, Timestamp timestampValue) {
    this.name = name;
    this.value = timestampValue;
    this.sqlType = Types.TIMESTAMP;
  }

  public Field(String name, Timestamp[] timestampValues) {
    this.name = name;
    this.value = timestampValues;
    this.sqlType = Types.ARRAY;
  }

  public Field(String name, BigDecimal bigDecimalValue) {
    this.name = name;
    this.value = bigDecimalValue;
    this.sqlType = Types.NUMERIC;
  }

  public Field(String name, Boolean booleanValue) {
    this.name = name;
    this.value = booleanValue;
    this.sqlType = Types.BOOLEAN;
  }

  public Field(String name, String value, CastType castType) {
    this.name = name;
    this.value = value;
    this.castType = castType;
    if (CastType.ARRAY.equals(castType)) {
      this.sqlType = Types.ARRAY;
    } else if (CastType.JSONB.equals(castType)) {
      this.sqlType = Types.VARCHAR;
    } else if (CastType.INTERVAL.equals(castType)) {
      this.sqlType = Types.OTHER;
      if (StringUtils.isBlank(value)) {
        this.value = null;
        isNull = true;
      }
    } else if (CastType.AS_IS.equals(castType)) {
      this.hasValue = false;
    }
  }

  public Field(String name, Object[] objectValues) {
    this.name = name;
    this.value = objectValues;
    this.sqlType = Types.JAVA_OBJECT;
  }

  public Field(String name, CastType castType, double latitude, double longitude) {
    this.name = name;
    this.castType = castType;
    if (CastType.GEOM.equals(castType)) {
      if (latitude == 0 && longitude == 0) {
        this.value = "NULL";
      } else {
        this.value = "ST_SetSRID(ST_MakePoint(" + latitude + ", " + longitude + "), 4326)";
      }
      this.sqlType = Types.OTHER;
    }
    this.hasValue = false;
  }

  public String getName() {
    return name;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public String getStringValue() {
    return value instanceof String ? (String) value : null;
  }

  public String[] getStringValues() {
    return value instanceof String[] ? (String[]) value : null;
  }

  public Long getLongValue() {
    return value instanceof Long ? (Long) value : null;
  }

  public Long[] getLongValues() {
    return value instanceof Long[] ? (Long[]) value : null;
  }

  public Integer getIntValue() {
    return value instanceof Integer ? (Integer) value : null;
  }

  public Double getDoubleValue() {
    return value instanceof Double ? (Double) value : null;
  }

  public Timestamp getTimestampValue() {
    return value instanceof Timestamp ? (Timestamp) value : null;
  }

  public Timestamp[] getTimestampValues() {
    return value instanceof Timestamp[] ? (Timestamp[]) value : null;
  }

  public Object[] getObjectValues() {
    return value instanceof Object[] ? (Object[]) value : null;
  }

  public BigDecimal getBigDecimalValue() {
    return value instanceof BigDecimal ? (BigDecimal) value : null;
  }

  public Boolean getBooleanValue() {
    return value instanceof Boolean ? (Boolean) value : null;
  }

  public int getSqlType() {
    return sqlType;
  }

  public boolean isNull() {
    return isNull;
  }

  public void setNull(boolean aNull) {
    isNull = aNull;
  }

  public boolean hasValue() {
    return hasValue;
  }

  public CastType getCastType() {
    return castType;
  }
}
