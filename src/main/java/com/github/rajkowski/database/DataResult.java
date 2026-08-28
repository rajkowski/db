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

import java.io.Serializable;
import java.util.List;

/**
 * Encapsulates mapped records and the total row count for paging-aware queries.
 *
 * @param <T> the mapped record type
 */
public class DataResult<T> implements Serializable {

  private static final long serialVersionUID = 8345648404174283569L;

  private List<T> records = null;
  private long totalRecordCount = -1;

  public DataResult() {
  }

  public List<T> getRecords() {
    return records;
  }

  public void setRecords(List<T> records) {
    this.records = records;
  }

  public boolean hasRecords() {
    return records != null && !records.isEmpty();
  }

  public long getTotalRecordCount() {
    return totalRecordCount;
  }

  public void setTotalRecordCount(long totalRecordCount) {
    this.totalRecordCount = totalRecordCount;
  }
}
