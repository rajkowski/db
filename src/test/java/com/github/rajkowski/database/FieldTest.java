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

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.postgresql.util.PGInterval;

import junit.framework.TestCase;

public class FieldTest extends TestCase {

  public void testSingleValueHoldsMultipleTypes() {
    Field stringField = new Field("name", "alpha");
    assertEquals("alpha", stringField.getValue());
    assertEquals("alpha", stringField.getStringValue());

    Field longField = new Field("count", 42L);
    assertEquals(Long.valueOf(42L), longField.getValue());
    assertEquals(Long.valueOf(42L), longField.getLongValue());

    Field intField = new Field("total", 7);
    assertEquals(Integer.valueOf(7), intField.getValue());
    assertEquals(Integer.valueOf(7), intField.getIntValue());

    Field boolField = new Field("enabled", Boolean.TRUE);
    assertEquals(Boolean.TRUE, boolField.getValue());
    assertEquals(Boolean.TRUE, boolField.getBooleanValue());

    Timestamp ts = new Timestamp(123456789L);
    Field timestampField = new Field("created_at", ts);
    assertEquals(ts, timestampField.getValue());
    assertEquals(ts, timestampField.getTimestampValue());

    BigDecimal price = new BigDecimal("12.34");
    Field decimalField = new Field("price", price);
    assertEquals(price, decimalField.getValue());
    assertEquals(price, decimalField.getBigDecimalValue());
  }

  public void testExplicitNullFieldIsConvertedToSqlNull() {
    Field nullableCount = new Field("count", 42L, true);

    assertTrue(nullableCount.isNull());
    assertNull(QuerySpec.readFieldValue(nullableCount));
  }

  public void testGeometryFieldUsesCastType() {
    Field geomField = new Field("location", CastType.GEOM, 12.5d, 45.0d);

    assertEquals("ST_SetSRID(ST_MakePoint(12.5, 45.0), 4326)", geomField.getValue());
    assertFalse(geomField.hasValue());
  }

  public void testArrayFieldKeepsArraySqlType() {
    Field arrayField = new Field("tags", new String[] { "pdf", "txt" });

    assertEquals(Types.ARRAY, arrayField.getSqlType());
    assertEquals(2, arrayField.getStringValues().length);
    assertEquals("pdf", arrayField.getStringValues()[0]);
    assertEquals("txt", arrayField.getStringValues()[1]);
  }

  public void testResultSetHelpersHandleNullsAndPeriodValues() throws SQLException {
    Map<String, Object> values = new HashMap<>();
    values.put("count", 42L);
    values.put("count_null", null);
    values.put("ratio", 12.5d);
    values.put("ratio_null", null);
    values.put("total", 7);
    values.put("total_null", null);
    values.put("period", new PGInterval(1, 2, 3, 4, 5, 6));
    values.put("period_null", null);

    ResultSet rs = buildResultSet(values);

    assertEquals(42L, DB.getLong(rs, "count", 99L));
    assertEquals(99L, DB.getLong(rs, "count_null", 99L));

    assertEquals(12.5d, DB.getDouble(rs, "ratio", 3.0d));
    assertEquals(3.0d, DB.getDouble(rs, "ratio_null", 3.0d));

    assertEquals(7, DB.getInt(rs, "total", 5));
    assertEquals(5, DB.getInt(rs, "total_null", 5));

    assertEquals("P1Y2M3DT4H5M6S", DB.getPeriod(rs, "period"));
    assertNull(DB.getPeriod(rs, "period_null"));
  }

  private static ResultSet buildResultSet(Map<String, Object> values) {
    final boolean[] lastWasNull = { false };
    return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
        (proxy, method, args) -> {
          String methodName = method.getName();
          if ("wasNull".equals(methodName)) {
            return lastWasNull[0];
          }
          if ("getLong".equals(methodName)) {
            Object value = valueForField(args, values);
            lastWasNull[0] = value == null;
            return value == null ? 0L : ((Number) value).longValue();
          }
          if ("getDouble".equals(methodName)) {
            Object value = valueForField(args, values);
            lastWasNull[0] = value == null;
            return value == null ? 0d : ((Number) value).doubleValue();
          }
          if ("getInt".equals(methodName)) {
            Object value = valueForField(args, values);
            lastWasNull[0] = value == null;
            return value == null ? 0 : ((Number) value).intValue();
          }
          if ("getObject".equals(methodName)) {
            Object value = valueForField(args, values);
            lastWasNull[0] = value == null;
            return value;
          }
          if ("toString".equals(methodName)) {
            return "ResultSetProxy";
          }
          return null;
        });
  }

  private static Object valueForField(Object[] args, Map<String, Object> values) {
    if (args == null || args.length == 0 || !(args[0] instanceof String fieldName)) {
      return null;
    }
    Object value = values.get(fieldName);
    return value;
  }
}
