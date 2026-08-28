# AGENTS.md

This repository is a small Java library for fluent SQL generation and JDBC parameter binding. Keep changes consistent with the existing builder-based API and the project’s lightweight design. PostgreSQL is the development database, but the tests run against local in-memory and JDBC-backed fixtures as needed.

## Project overview

- Main implementation: [src/main/java/com/github/rajkowski/database](src/main/java/com/github/rajkowski/database)
- Tests: [src/test/java/com/github/rajkowski/database](src/test/java/com/github/rajkowski/database)
- Build configuration: [pom.xml](pom.xml)
- User-facing examples and API overview: [README.md](README.md)

## Build and test

Run the repository checks before considering work complete:

- `mvn compile`
- `mvn test`

Prefer extending the existing JUnit 4 suite under [src/test/java/com/github/rajkowski/database](src/test/java/com/github/rajkowski/database) rather than introducing a new test framework or style.

## Conventions that matter

- Keep the API fluent and chainable: builder methods mutate query state and return `this`.
- Prefer parameterized SQL via `QuerySpec`, `Field`, `Table`, and `?` placeholders instead of string concatenation.
- Preserve the static entry points and naming patterns such as `DB.SELECT(...)`, `DB.INSERT()`, `DB.UPDATE(table)`, and `DB.DELETE()`.
- Respect the safety checks: identifiers and clauses are validated and unsafe fragments such as `;` or inline literals are rejected.
- Favor small, focused modifications to the existing builder approach over introducing new wrappers or architectural layers.

## Usage patterns to preserve

- Build queries with chained clauses such as `.FROM(...)`, `.WHERE("id = ?", 7)`, `.AND(...)`, `.ORDER_BY(...)`, and `.PAGING(...)`.
- Use `Field` objects for insert and update values, for example `new Field("name", "alice")`.
- Execute via the `QuerySpec` helpers like `.execute()`, `.executeQuery()`, `.executeUpdate()`, `.returnList(...)`, and `.returnRecord(...)`.
- Keep tenant and datasource scoping in the thread-local helpers inside `DB`, including `withConnection(...)`, `withDataSource(...)`, `withTenant(...)`, and `withTenantDataSource(...)`.

## Typical edit targets

- SQL builders: [src/main/java/com/github/rajkowski/database/Select.java](src/main/java/com/github/rajkowski/database/Select.java), [src/main/java/com/github/rajkowski/database/Insert.java](src/main/java/com/github/rajkowski/database/Insert.java), [src/main/java/com/github/rajkowski/database/Update.java](src/main/java/com/github/rajkowski/database/Update.java), [src/main/java/com/github/rajkowski/database/Delete.java](src/main/java/com/github/rajkowski/database/Delete.java)
- Core execution and datasource logic: [src/main/java/com/github/rajkowski/database/DB.java](src/main/java/com/github/rajkowski/database/DB.java)
- Value handling and typing: [src/main/java/com/github/rajkowski/database/Field.java](src/main/java/com/github/rajkowski/database/Field.java)
- Shared query assembly and parameter tracking: [src/main/java/com/github/rajkowski/database/QuerySpec.java](src/main/java/com/github/rajkowski/database/QuerySpec.java)
- Table and identifier helpers: [src/main/java/com/github/rajkowski/database/Table.java](src/main/java/com/github/rajkowski/database/Table.java)

## Working expectations

- Keep changes small and focused.
- Match the project’s existing naming and code style.
- If a bug affects SQL generation or parameter binding, verify it with the relevant test case and keep the fix minimal.
- Prefer extending the current APIs rather than redesigning the library surface.
- When updating tests or documentation, use examples that reflect the actual builder API and JDBC behavior already validated in the existing test suite.
