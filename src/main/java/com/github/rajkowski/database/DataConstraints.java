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
import java.util.ArrayList;
import java.util.List;

/**
 * Carries the pagination and sorting state that can be passed through query builders.
 *
 * <p>The class intentionally mirrors the legacy public API while also exposing the
 * smaller helper methods needed by the fluent SQL builders.</p>
 */
public class DataConstraints extends Paging implements Serializable {
  private static final long serialVersionUID = 8345648404174283569L;

  private int pageNumber = 1;
  private int pageSize = -1;
  private long totalRecordCount = -1L;
  private int maxPageNumber = 1;
  private String defaultColumnToSortBy = null;
  private String[] columnsToSortBy = null;
  private String[] sortOrder = null;
  private boolean useCount = true;

  public DataConstraints() {
    super(1, 1);
  }

  public DataConstraints(int pageNumber, int pageSize) {
    super(pageNumber > 0 ? pageNumber : 1, pageSize > 0 ? pageSize : 1);
    this.pageNumber = pageNumber > 0 ? pageNumber : 1;
    this.pageSize = pageSize;
    updateMaxPageNumber();
  }

  public DataConstraints(int pageNumber, int pageSize, String columnToSortBy) {
    this(pageNumber, pageSize);
    setColumnToSortBy(columnToSortBy, null);
  }

  public DataConstraints(int pageNumber, int pageSize, String columnToSortBy, String ascOrDesc) {
    this(pageNumber, pageSize);
    setColumnToSortBy(columnToSortBy, ascOrDesc);
  }

  @Override
  public int getPageNumber() {
    return pageNumber;
  }

  @Override
  public void setPageNumber(int pageNumber) {
    this.pageNumber = pageNumber;
    if (this.pageNumber < 1) {
      this.pageNumber = 1;
    }
  }

  public String getPageNumberAsString() {
    return String.valueOf(pageNumber);
  }

  @Override
  public int getPageSize() {
    return pageSize;
  }

  @Override
  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
    updateMaxPageNumber();
  }

  @Override
  public int getOffset() {
    if (pageSize < 1) {
      return 0;
    }
    return (pageNumber - 1) * pageSize;
  }

  @Override
  public long getTotalCount() {
    return totalRecordCount;
  }

  @Override
  public void setTotalCount(long totalCount) {
    this.totalRecordCount = totalCount;
    updateMaxPageNumber();
  }

  public long getTotalRecordCount() {
    return totalRecordCount;
  }

  public void setTotalRecordCount(long totalRecordCount) {
    this.totalRecordCount = totalRecordCount;
    updateMaxPageNumber();
  }

  public int getMaxPageNumber() {
    return maxPageNumber;
  }

  public String getDefaultColumnToSortBy() {
    return defaultColumnToSortBy;
  }

  /**
   * Used by the repository objects to define a default sort.
   */
  public DataConstraints setDefaultColumnToSortBy(String columnToSortBy) {
    this.defaultColumnToSortBy = columnToSortBy;
    return this;
  }

  /**
   * Used by the application to override the default sort.
   */
  public void setColumnToSortBy(String name) {
    columnsToSortBy = new String[] { name };
  }

  /**
   * Used by the application to override the default sort.
   */
  public void setColumnToSortBy(String name, String ascOrDesc) {
    columnsToSortBy = new String[] { name };
    if ("desc".equalsIgnoreCase(ascOrDesc)) {
      sortOrder = new String[] { "desc" };
    } else if ("asc".equalsIgnoreCase(ascOrDesc)) {
      sortOrder = new String[] { "asc" };
    } else {
      sortOrder = null;
    }
  }

  public String[] getColumnsToSortBy() {
    return columnsToSortBy;
  }

  /**
   * Used by the application to override the default sort.
   */
  public void setColumnsToSortBy(String[] columnsToSortBy) {
    this.columnsToSortBy = columnsToSortBy;
  }

  public String[] getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(String[] sortOrder) {
    this.sortOrder = sortOrder;
  }

  public boolean containsColumnToSortBy(String name) {
    if (columnsToSortBy == null || name == null) {
      return false;
    }
    for (String column : columnsToSortBy) {
      if (name.equals(column)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasSortOrder() {
    return defaultColumnToSortBy != null || columnsToSortBy != null;
  }

  public List<String> getPageList() {
    int current = Math.max(1, pageNumber);
    int last = Math.max(1, maxPageNumber);
    int delta = 2;
    int left = current - delta;
    int right = current + delta + 1;
    List<String> range = new ArrayList<>();
    List<String> rangeWithDots = new ArrayList<>();
    int previous = 0;

    for (int i = 1; i <= last; i++) {
      if (i == 1 || i == last || (i >= left && i < right)) {
        range.add(String.valueOf(i));
      }
    }

    for (String item : range) {
      int page = Integer.parseInt(item);
      if (previous > 0) {
        int gap = page - previous;
        if (gap == 2) {
          rangeWithDots.add(String.valueOf(previous + 1));
        } else if (gap != 1) {
          rangeWithDots.add("...");
        }
      }
      rangeWithDots.add(item);
      previous = page;
    }

    return rangeWithDots;
  }

  public boolean useCount() {
    return useCount;
  }

  public DataConstraints setUseCount(boolean useCount) {
    this.useCount = useCount;
    return this;
  }

  private void updateMaxPageNumber() {
    if (totalRecordCount > 0 && pageSize > 0) {
      maxPageNumber = (int) ((totalRecordCount + pageSize - 1L) / pageSize);
      if (maxPageNumber < 1) {
        maxPageNumber = 1;
      }
    } else {
      maxPageNumber = 1;
    }
  }
}
