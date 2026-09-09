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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Coordinates the physical connection budget shared by tenant datasources.
 *
 * <p>Tenants are assigned to a "pool group". Tenants that share a pool group compete for that
 * group's connection budget (for example, several tenant schemas on the same database server).
 * Tenants registered under their own distinct pool group are effectively dedicated: they are not
 * throttled against any other tenant's budget and are limited only by their own datasource's pool
 * size.
 */
public class TenantConnectionManager {

  private static final long DEFAULT_WAIT_MILLIS = 30_000L;
  private static final String DEFAULT_POOL_GROUP = "__default__";

  private final Map<String, PoolGroup> poolGroups = new ConcurrentHashMap<>();
  private final Map<String, String> tenantPoolGroups = new ConcurrentHashMap<>();
  private final Object capacityLock = new Object();
  private long accessSequence;

  /**
   * Sets the maximum number of physical connections shared by tenants in the default pool group.
   *
   * @param maximumConnections the maximum number of connections
   */
  public void setMaximumConnections(int maximumConnections) {
    setMaximumConnections(DEFAULT_POOL_GROUP, maximumConnections);
  }

  /**
   * Sets the maximum number of physical connections shared by tenants registered under the given
   * pool group.
   *
   * @param poolGroup the pool group identifier
   * @param maximumConnections the maximum number of connections
   */
  public void setMaximumConnections(String poolGroup, int maximumConnections) {
    if (maximumConnections < 1) {
      throw new IllegalArgumentException("Maximum tenant connections must be greater than zero");
    }
    synchronized (capacityLock) {
      groupFor(normalizePoolGroup(poolGroup)).maximumConnections = maximumConnections;
      capacityLock.notifyAll();
    }
  }

  /**
   * Registers a datasource for a tenant id under the default pool group.
   *
   * @param tenantId the tenant identifier
   * @param dataSource the datasource to register
   */
  public void register(String tenantId, DataSource dataSource) {
    register(tenantId, dataSource, DEFAULT_POOL_GROUP);
  }

  /**
   * Registers a datasource for a tenant id under the given pool group. Tenants registered under
   * the same pool group share that group's connection budget. Register a tenant under a pool
   * group used by no other tenant (for example, the tenant id itself) to give it a dedicated,
   * unshared budget.
   *
   * @param tenantId the tenant identifier
   * @param dataSource the datasource to register
   * @param poolGroup the pool group identifier that this tenant's budget is shared with
   */
  public void register(String tenantId, DataSource dataSource, String poolGroup) {
    if (dataSource == null) {
      throw new IllegalArgumentException("DataSource cannot be null");
    }
    synchronized (capacityLock) {
      String normalizedTenantId = normalizeTenantId(tenantId);
      String normalizedPoolGroup = normalizePoolGroup(poolGroup);
      unregisterLocked(normalizedTenantId);
      tenantPoolGroups.put(normalizedTenantId, normalizedPoolGroup);
      groupFor(normalizedPoolGroup).pools.put(normalizedTenantId, new TenantPool(dataSource, ++accessSequence));
      capacityLock.notifyAll();
    }
  }

  public DataSource getDataSource(String tenantId) {
    TenantPool pool = findPool(tenantId);
    return pool == null ? null : pool.dataSource;
  }

  public void unregister(String tenantId) {
    synchronized (capacityLock) {
      unregisterLocked(normalizeTenantId(tenantId));
      capacityLock.notifyAll();
    }
  }

  private void unregisterLocked(String normalizedTenantId) {
    String previousPoolGroup = tenantPoolGroups.remove(normalizedTenantId);
    if (previousPoolGroup == null) {
      return;
    }
    PoolGroup group = poolGroups.get(previousPoolGroup);
    if (group == null) {
      return;
    }
    TenantPool previous = group.pools.remove(normalizedTenantId);
    if (previous != null) {
      group.retiredPools.add(previous);
    }
  }

  public void clear() {
    synchronized (capacityLock) {
      poolGroups.clear();
      tenantPoolGroups.clear();
      capacityLock.notifyAll();
    }
  }

  public Connection getConnection(String tenantId) throws SQLException {
    String normalizedTenantId = normalizeTenantId(tenantId);
    long deadline = System.currentTimeMillis() + DEFAULT_WAIT_MILLIS;
    synchronized (capacityLock) {
      String poolGroupId = tenantPoolGroups.get(normalizedTenantId);
      if (poolGroupId == null) {
        throw new SQLException("No DataSource registered for tenant: " + tenantId);
      }
      PoolGroup group = groupFor(poolGroupId);
      TenantPool requestedPool = group.pools.get(normalizedTenantId);
      if (requestedPool == null) {
        throw new SQLException("No DataSource registered for tenant: " + tenantId);
      }
      while (true) {
        removeClosedRetiredPools(group);
        requestedPool.lastAccess = ++accessSequence;
        evictIdlePools(group, requestedPool);
        if (availableFor(group, requestedPool)) {
          Connection connection = requestedPool.dataSource.getConnection();
          requestedPool.activeLeases++;
          return managedConnection(connection, group, requestedPool);
        }
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
          throw new SQLException("Tenant connection budget is exhausted");
        }
        try {
          capacityLock.wait(Math.min(remaining, 100L));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new SQLException("Interrupted while waiting for a tenant connection", e);
        }
      }
    }
  }

  private PoolGroup groupFor(String normalizedPoolGroup) {
    return poolGroups.computeIfAbsent(normalizedPoolGroup, key -> new PoolGroup());
  }

  private TenantPool findPool(String tenantId) {
    String normalizedTenantId = normalizeTenantId(tenantId);
    String poolGroupId = tenantPoolGroups.get(normalizedTenantId);
    if (poolGroupId == null) {
      return null;
    }
    PoolGroup group = poolGroups.get(poolGroupId);
    return group == null ? null : group.pools.get(normalizedTenantId);
  }

  private boolean availableFor(PoolGroup group, TenantPool requestedPool) {
    if (!(requestedPool.dataSource instanceof HikariDataSource)) {
      return totalActiveLeases(group) < group.maximumConnections;
    }
    HikariDataSource hikariDataSource = (HikariDataSource) requestedPool.dataSource;
    return hikariDataSource.getHikariPoolMXBean().getIdleConnections() > 0
        || totalPhysicalConnections(group) < group.maximumConnections;
  }

  private void evictIdlePools(PoolGroup group, TenantPool requestedPool) {
    if (requestedPool.dataSource instanceof HikariDataSource
        && ((HikariDataSource) requestedPool.dataSource).getHikariPoolMXBean().getIdleConnections() == 0) {
      allPools(group).stream()
          .filter(pool -> pool != requestedPool)
          .filter(this::isIdle)
          .sorted(Comparator.comparingLong(pool -> pool.lastAccess))
          .filter(pool -> totalPhysicalConnections(group) >= group.maximumConnections)
          .filter(pool -> pool.dataSource instanceof HikariDataSource)
          .findFirst()
          .ifPresent(pool -> ((HikariDataSource) pool.dataSource).getHikariPoolMXBean().softEvictConnections());
    }
  }

  private boolean isIdle(TenantPool pool) {
    if (pool.dataSource instanceof HikariDataSource) {
      HikariDataSource dataSource = (HikariDataSource) pool.dataSource;
      return dataSource.getHikariPoolMXBean().getActiveConnections() == 0
          && dataSource.getHikariPoolMXBean().getIdleConnections() > 0;
    }
    return pool.activeLeases == 0;
  }

  private int totalPhysicalConnections(PoolGroup group) {
    return allPools(group).stream()
        .filter(pool -> pool.dataSource instanceof HikariDataSource)
        .mapToInt(pool -> ((HikariDataSource) pool.dataSource).getHikariPoolMXBean().getTotalConnections())
        .sum();
  }

  private int totalActiveLeases(PoolGroup group) {
    return allPools(group).stream().mapToInt(pool -> pool.activeLeases).sum();
  }

  private List<TenantPool> allPools(PoolGroup group) {
    List<TenantPool> allPools = new java.util.ArrayList<>(group.pools.values());
    allPools.addAll(group.retiredPools);
    return allPools;
  }

  private void removeClosedRetiredPools(PoolGroup group) {
    group.retiredPools.removeIf(pool -> pool.activeLeases == 0
        && (!(pool.dataSource instanceof HikariDataSource)
            || ((HikariDataSource) pool.dataSource).getHikariPoolMXBean().getTotalConnections() == 0));
  }

  private Connection managedConnection(Connection connection, PoolGroup group, TenantPool pool) {
    InvocationHandler handler = new ManagedConnectionHandler(connection, () -> release(group, pool));
    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
  }

  private void release(PoolGroup group, TenantPool pool) {
    synchronized (capacityLock) {
      pool.activeLeases--;
      removeClosedRetiredPools(group);
      capacityLock.notifyAll();
    }
  }

  private static String normalizeTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("Tenant id cannot be null or blank");
    }
    return tenantId.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static String normalizePoolGroup(String poolGroup) {
    if (poolGroup == null || poolGroup.isBlank()) {
      return DEFAULT_POOL_GROUP;
    }
    return poolGroup.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static final class PoolGroup {
    private final Map<String, TenantPool> pools = new ConcurrentHashMap<>();
    private final List<TenantPool> retiredPools = new CopyOnWriteArrayList<>();
    private volatile int maximumConnections = Integer.MAX_VALUE;
  }

  private static final class TenantPool {
    private final DataSource dataSource;
    private long lastAccess;
    private int activeLeases;

    private TenantPool(DataSource dataSource, long lastAccess) {
      this.dataSource = dataSource;
      this.lastAccess = lastAccess;
    }
  }

  private static final class ManagedConnectionHandler implements InvocationHandler {
    private final Connection delegate;
    private final Runnable release;
    private boolean closed;

    private ManagedConnectionHandler(Connection delegate, Runnable release) {
      this.delegate = delegate;
      this.release = release;
    }

    @Override
    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
      try {
        if ("close".equals(method.getName())) {
          try {
            return method.invoke(delegate, args);
          } finally {
            if (!closed) {
              closed = true;
              release.run();
            }
          }
        }
        return method.invoke(delegate, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }
}