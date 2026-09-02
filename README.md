# DB

A small Java library for fluent SQL generation and JDBC parameter binding.

This project focuses on building safe, parameterized SQL statements with a chainable builder style for SELECT, INSERT, UPDATE, and DELETE operations.

Intended for clients which use testing for their SQL and can use these efficient methods to turn result sets into objects, execute queries simply, and access metrics as needed.

## Features

- Fluent SQL builders for common CRUD operations
- Parameterized WHERE clauses and bound values
- Typed `Field` values for insert/update handling
- JDBC-friendly execution helpers
- PostgreSQL is the development database
- Java 21 project

## Project layout

- `src/main/java/com/github/rajkowski/database` — library implementation
- `src/test/java/com/github/rajkowski/database` — JUnit tests

## Build

```bash
mvn compile
mvn test
```

## Real world example

```java

private static Table BLOGS_TABLE = "blogs";

private static Blog buildRecord(ResultSet rs) {
    try {
        Blog blog = new Blog();
        blog.setId(rs.getLong("id"));
        blog.setTitle(rs.getString("title"));
        return blog;
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}

Blog blog = DB.SELECT("*")
    .FROM(BLOGS_TABLE)
    .WHERE("id = ?", 42)
    .returnRecord(BlogRepository::buildRecord);
```

For a list of blogs...

```java
Paging paging = new Paging(1, 20);

List<Blog> blogs = DB.SELECT("*")
    .FROM(BLOGS_TABLE)
    .ORDER_BY("id ASC")
    .PAGING(paging)
    .returnList(BlogRepository::buildRecord);

long totalRecords = paging.getTotalCount();
```

## Example usage

The core API is fluent and chainable. Builders return a `QuerySpec`, which can be executed directly or used with `executeQuery(...)`, `executeUpdate(...)`, `returnList(...)`, and `returnRecord(...)`.

```java
private static Table USERS_TABLE = "users";

DB.INSERT()
    .INTO(USERS_TABLE)
    .FIELD("name", "alice")
    .FIELD("email", "alice@example.com")

DB.UPDATE(USERS_TABLE)
    .SET("name", "alice")
    .SET("email", "alice@example.com")
    .WHERE("id = ?", 10L)
    .AND("active = ?", true);
```

```java
import com.github.rajkowski.database.DB;
import com.github.rajkowski.database.Field;

private static Table USERS_TABLE = "users";

Select selectSpec = DB.SELECT("id", "name")
    .FROM(USERS_TABLE)
    .WHERE("active = ?", true)
    .ORDER_BY("name ASC");

String sql = selectSpec.getSql();
System.out.println(sql);

Insert insertSpec = DB.INSERT()
    .INTO(USERS_TABLE)
    .FIELD("name", "alice")
    .FIELD("active", true);

long generatedId = insertSpec.execute();

Update updateSpec = DB.UPDATE(USERS_TABLE)
    .SET("name", "bob")
    .WHERE("id = ?", 42L);

int updateCount = updateSpec.execute();

Delete deleteSpec = DB.DELETE().FROM(USERS_TABLE).WHERE("id = ?", 42L);

int deleteCount = deleteSpec.execute();
```

### Read a single value or mapped record

```java
DB.withDataSource(dataSource, () -> {
    try {
        Object userName = DB.SELECT("name")
            .FROM(USERS_TABLE)
            .WHERE("id = ?", 7L)
            .executeQuery();

        String firstUser = DB.SELECT("name")
            .FROM(USERS_TABLE)
            .WHERE("id = ?", 7L)
            .returnRecord(rs -> rs.getString("name"));
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

### Access query metrics after execution

The static DB methods return a `QuerySpec`, so you can build the query, run it, and then inspect its timing and execution metadata. Metrics are captured on the query instance itself.

```java
import com.github.rajkowski.database.DB;
import com.github.rajkowski.database.QueryMetrics;
import com.github.rajkowski.database.QuerySpec;

DB.withDataSource(dataSource, () -> {
    try {
        Select select = DB.SELECT("id", "name")
            .FROM(USERS_TABLE)
            .WHERE("active = ?", true)
            .ORDER_BY("id ASC");

        Object result = select.executeQuery();

        QueryMetrics metrics = select.getMetrics();
        System.out.println(metrics.getSql());
        System.out.println(metrics.getParameterCount());
        System.out.println(metrics.getRowsAffected());
        System.out.println(metrics.getExecutionTimeMs());
        System.out.println(metrics.isSuccess());
        System.out.println(metrics.getStatus());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

This is the simplest way to inspect execution details after a query without wrapping the DB call in custom instrumentation.

### UPDATE and DELETE metrics

Mutation operations update the same metric snapshot used for reads. In particular, `rowsAffected` is filled after `UPDATE` and `DELETE` statements, so you can verify whether the statement changed the expected number of records.

```java
DB.withDataSource(dataSource, () -> {
    try {
        Update update = DB.UPDATE(USERS_TABLE)
            .SET("name", "bob")
            .WHERE("id = ?", 42L);

        int updatedRows = update.execute();
        System.out.println(update.getRowsAffected()); // 1 for a matching row
        System.out.println(update.getMetrics().getRowsAffected());
        System.out.println(update.getMetrics().getStatus()); // SUCCESS

        Delete delete = DB.DELETE()
            .FROM(USERS_TABLE)
            .WHERE("id = ?", 42L);

        int deletedRows = delete.execute();
        System.out.println(delete.getRowsAffected());
        System.out.println(delete.getMetrics().getRowsAffected());
        System.out.println(delete.getMetrics().getExecutionTimeMs());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

If an update or delete fails, the metric state flips to a failed result and the `status` is recorded while `rowsAffected` is reset to `0` for the failed execution.

### Aggregate queries

```java
DB.withDataSource(dataSource, () -> {
    try {
        Object count = DB.COUNT("*")
            .FROM(USERS_TABLE)
            .WHERE("active = ?", true)
            .executeQuery();
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

## Connection and datasource usage

The library supports three execution contexts: a direct connection, a default datasource, or a tenant-scoped datasource. The active context is resolved automatically, and the library closes only connections it opened itself.

### Use a specific connection for a request or transaction

```java
import java.sql.Connection;
import com.github.rajkowski.database.DB;

try (Connection connection = dataSource.getConnection()) {
    DB.withConnection(connection, () -> {
        try {
            DB.INSERT()
                .INTO(USERS_TABLE)
                .FIELDS("name", "alice")
                .execute();

            Object userName = DB.SELECT("name")
                .FROM(USERS_TABLE)
                .WHERE("id = ?", 1)
                .executeQuery();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });
}
```

This is useful when the caller already owns a transaction or wants all queries in a request to share the same JDBC connection.

### Configure a default datasource

```java
DB.setDataSource(dataSource);

DB.withDataSource(dataSource, () -> {
    try {
        Object count = DB.SELECT("COUNT(*)")
            .FROM(USERS_TABLE)
            .executeQuery();
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

You can also scope a datasource without altering the global default:

```java
DB.withDataSource(dataSource, () -> {
    try {
        Object value = DB.SELECT("name")
            .FROM(USERS_TABLE)
            .WHERE("id = ?", 7)
            .executeQuery();
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

### Multi-tenant registry

For multi-tenant systems, create a tenant registry at application startup and register one datasource per tenant. The application can then select the tenant for the current web request and let all subsequent database operations resolve against that tenant's datasource until the request finishes.

Applications can register and inspect tenant sources through the `DB` facade while keeping registry storage encapsulated:

```java
DB.registerTenantDataSource("tenant-42", tenantDataSourceA);
Set<String> tenantIds = DB.getTenantIds();
```

`getTenantIds()` returns an immutable snapshot. Registering an existing tenant replaces only that tenant's current datasource; the caller remains responsible for any datasource lifecycle outside the registry.

```java
TenantRegistry registry = new TenantRegistry();
registry.register("tenant-42", tenantDataSourceA);
registry.register("tenant-99", tenantDataSourceB);

DB.setTenantRegistry(registry);
```

At the start of a request, resolve the tenant id from the request context and activate it once:

```java
String tenantId = request.getHeader("X-Tenant-Id");
DB.setTenant(tenantId);

try {
    DB.INSERT()
        .INTO(USERS_TABLE)
        .FIELDS("name", "bob")
        .execute();

    Object userName = DB.SELECT("name")
        .FROM(USERS_TABLE)
        .WHERE("id = ?", 7L)
        .executeQuery();
} finally {
    DB.clearTenant();
}
```

This request-scoped selection is equivalent to a temporary tenant scope, but it is convenient when the web framework decides the tenant once and then leaves the rest of the request to use the currently selected tenant without repeating `withTenant(...)` around every query.

You can also temporarily scope a tenant within a callback:

```java
DB.withTenant("tenant-42", () -> {
    try {
        DB.INSERT()
            .INTO(USERS_TABLE)
            .FIELDS("name", "bob")
            .execute();
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

You can also bind an already-known datasource directly:

```java
DB.withTenantDataSource(tenantDataSourceA, () -> {
    try {
        Object result = DB.SELECT("name")
            .FROM(USERS_TABLE)
            .WHERE("id = ?", 1)
            .executeQuery();
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
});
```

### Best practices

- Never share a single `Connection` across threads.
- Prefer one `HikariDataSource` per tenant database for multi-tenant deployments.
- Use `withConnection(...)`, `withDataSource(...)`, or `withTenant(...)` around request-scoped work so thread-local state is restored automatically.
- Keep the default datasource for global/shared database access and reserve tenant-scoped overrides for tenant-specific workloads.
- Clear thread-local state in a `finally` block when the caller manages the connection manually.
