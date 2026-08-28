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
import java.util.concurrent.TimeUnit;

/**
 * Snapshot of the last execution result for a query specification.
 */
public class QueryMetrics implements Serializable {

  private static final long serialVersionUID = 1L;

  private String sql;
  private int parameterCount;
  private int rowsAffected;
  private long executionTime;
  private boolean success;
  private String status;

  public QueryMetrics() {
    this.status = "SUCCESS";
    this.success = true;
  }

  public String getSql() {
    return sql;
  }

  public void setSql(String sql) {
    this.sql = sql;
  }

  public int getParameterCount() {
    return parameterCount;
  }

  public void setParameterCount(int parameterCount) {
    this.parameterCount = parameterCount;
  }

  public int getRowsAffected() {
    return rowsAffected;
  }

  public void setRowsAffected(int rowsAffected) {
    this.rowsAffected = Math.max(0, rowsAffected);
  }

  public long getExecutionTime() {
    return executionTime;
  }

  public long getExecutionTimeMs() {
    return TimeUnit.NANOSECONDS.toMillis(executionTime);
  }

  public void setExecutionTime(long executionTime) {
    this.executionTime = Math.max(0L, executionTime);
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
    if (success) {
      this.status = "SUCCESS";
    }
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status == null ? "FAILED" : status;
    this.success = "SUCCESS".equalsIgnoreCase(this.status);
  }

  @Override
  public String toString() {
    return "QueryMetrics{" +
        "sql='" + sql + '\'' +
        ", rowsAffected=" + rowsAffected +
        ", executionTimeMs=" + getExecutionTimeMs() +
        ", success=" + success +
        ", status='" + status + '\'' +
        '}';
  }
}
