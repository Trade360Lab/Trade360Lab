# Database Migration Workflow

Target release: `v0.9.2-alpha.1`

Trade360Lab now includes a Flyway migration baseline for Java-managed schema evolution while keeping existing local startup simple.

Current files:

- `backend/java/src/main/resources/db/migration/V1__baseline.sql`
- `backend/java/src/main/resources/db/migration/V2__live_trading_audit.sql`
- `backend/java/src/main/resources/schema.sql`

Rules for future migrations:

- Add only additive migrations unless a destructive change has an explicit release warning and recovery plan.
- Name files with Flyway ordering, for example `V3__add_column_name.sql`.
- Keep `schema.sql` aligned while local alpha startup still uses Spring SQL init.
- Validate with `mvn -B test` and Docker Compose smoke checks.
