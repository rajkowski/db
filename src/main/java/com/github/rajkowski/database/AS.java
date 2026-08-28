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
 * Value object representing a validated SQL alias such as a derived table name.
 */
public final class AS {
  private final String alias;

  /**
   * Creates an alias value from the supplied identifier.
   *
   * @param alias the alias to validate
   */
  public AS(String alias) {
    this.alias = validate(alias);
  }

  /**
   * Creates a validated alias value object.
   *
   * @param alias the alias to validate
   * @return a validated alias value
   */
  public static AS of(String alias) {
    return new AS(alias);
  }

  /**
   * Returns the validated alias text.
   *
   * @return the alias name
   */
  public String getAlias() {
    return alias;
  }

  @Override
  public String toString() {
    return alias;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AS)) {
      return false;
    }
    AS as = (AS) o;
    return alias.equals(as.alias);
  }

  @Override
  public int hashCode() {
    return Objects.hash(alias);
  }

  private static String validate(String alias) {
    if (alias == null || alias.trim().isEmpty()) {
      throw new IllegalArgumentException("Alias cannot be empty.");
    }
    String trimmed = alias.trim();
    if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
      throw new IllegalArgumentException("Unsafe alias detected.");
    }
    if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("Invalid alias: " + alias);
    }
    return trimmed;
  }
}
