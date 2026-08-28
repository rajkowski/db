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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

/**
 * Registry for tenant-specific datasources
 */
public class TenantRegistry {

  private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

  /**
   * Registers a datasource for a tenant id.
   *
   * @param tenantId the tenant identifier to associate with the datasource
   * @param dataSource the datasource to register
   */
  public void register(String tenantId, DataSource dataSource) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("Tenant id cannot be null or blank");
    }
    if (dataSource == null) {
      throw new IllegalArgumentException("DataSource cannot be null");
    }
    dataSources.put(tenantId, dataSource);
  }

  /**
   * Looks up the datasource registered for a tenant id.
   *
   * @param tenantId the tenant identifier to resolve
   * @return the datasource for the tenant, or null if not registered
   */
  public DataSource getDataSource(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("Tenant id cannot be null or blank");
    }
    return dataSources.get(tenantId);
  }

  /**
   * Removes a tenant datasource from the registry.
   *
   * @param tenantId the tenant identifier to unregister
   */
  public void unregister(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("Tenant id cannot be null or blank");
    }
    dataSources.remove(tenantId);
  }

  /**
   * Removes every tenant registration from the registry.
   */
  public void clear() {
    dataSources.clear();
  }

  /**
   * Checks whether a tenant id has a datasource registered.
   *
   * @param tenantId the tenant identifier to look up
   * @return true when the tenant is registered
   */
  public boolean contains(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("Tenant id cannot be null or blank");
    }
    return dataSources.containsKey(tenantId);
  }
}
