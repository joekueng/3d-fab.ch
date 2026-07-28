# Database migrations

Flyway is the only schema migration mechanism for the backend. Hibernate uses
`ddl-auto=validate` and must never modify production schemas.

## Rules

- Add a new `V<version>__<description>.sql` file for every schema change.
- Never edit a versioned migration after it has been applied to a shared
  environment.
- Keep the JPA mapping and its migration in the same change.
- Prefer forward-only fixes. Back up data before destructive migrations.
- Do not use `flyway clean` against shared environments.

## Existing database bootstrap

`V1__baseline_schema.sql` creates an empty database. Existing non-empty
databases must be compared with V1 and baselined explicitly at version 1.

For the controlled first startup only:

```bash
FLYWAY_BASELINE_ON_MIGRATE=true
```

After `flyway_schema_history` records the baseline and pending migrations have
run successfully, remove that setting and return it to `false`.

Before any shared-environment bootstrap:

1. Create and verify a PostgreSQL backup.
2. Capture a schema-only dump.
3. Compare the environment schema with V1.
4. Run the first startup in dev, then int, then prod.
5. Verify `flyway_schema_history` and Hibernate schema validation.

The current V1 was generated from the local PostgreSQL 15 database and must not
be promoted until the dev, int, and prod schema-only dumps have been reconciled.
