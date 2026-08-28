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

import java.util.Objects;

/**
 * Represents a SQL cast type, used for casting column values in queries.
 */
public final class CastType {

  public static final CastType ARRAY = new CastType("ARRAY");
  public static final CastType GEOM = new CastType("GEOM");
  public static final CastType JSONB = new CastType("JSONB");
  public static final CastType INTERVAL = new CastType("INTERVAL");
  public static final CastType AS_IS = new CastType("AS_IS");

  private final String value;

  public CastType(String value) {
    this.value = Objects.requireNonNull(value, "Cast type cannot be null.");
  }

  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CastType castType)) {
      return false;
    }
    return value.equals(castType.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
