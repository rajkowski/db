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
 * Value object representing a validated SQL table name
 */
public final class Table {
  private final String name;

  /**
   * Creates a table value from the supplied name.
   *
   * @param name the table name
   */
  public Table(String name) {
    this.name = QuerySpec.sanitizeIdentifier(name);
  }

  /**
   * Creates a table value from the supplied name.
   *
   * @param name the table name
   * @return a validated table value object
   */
  public static Table of(String name) {
    return new Table(name);
  }

  /**
   * Returns the validated table name.
   *
   * @return the table name
   */
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Table)) {
      return false;
    }
    Table table = (Table) o;
    return name.equals(table.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}