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

/**
 * Encapsulates a requested page number and page size, plus the total row count returned by the database.
 */
public class Paging {
  private int pageNumber;
  private int pageSize;
  private long totalCount;

  public Paging() {
    this(1, 1);
  }

  public Paging(int pageNumber, int pageSize) {
    setPageNumber(pageNumber);
    setPageSize(pageSize);
  }

  public int getPageNumber() {
    return pageNumber;
  }

  public void setPageNumber(int pageNumber) {
    if (pageNumber < 1) {
      throw new IllegalArgumentException("Page number must be at least 1.");
    }
    this.pageNumber = pageNumber;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    if (pageSize < 1) {
      throw new IllegalArgumentException("Page size must be at least 1.");
    }
    this.pageSize = pageSize;
  }

  public int getOffset() {
    return (pageNumber - 1) * pageSize;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }
}
