# Backend

Spring Boot 3 (Java 21, Maven). See `docs/2026-11-13-pos-restaurante-design.md` for the product spec and `docs/superpowers/plans/2026-11-19-backend-fundamentos.md` for the implementation plan this was built from.

## Running the tests

Requires a working Docker daemon (integration tests run against real Postgres via Testcontainers) and two environment variables:

```bash
export JWT_SECRET="<any string, 32+ bytes recommended>"
mvn test
```

`SUPABASE_DB_URL` / `SUPABASE_DB_USERNAME` / `SUPABASE_DB_PASSWORD` are required by `application.yml` but are overridden by Testcontainers for tests (see `AbstractIntegrationTest`), so they don't need to be set to run the suite locally — only `JWT_SECRET` does.

## Running the app

Needs all four: `JWT_SECRET`, `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD` (pointing at the real Supabase Postgres instance).

Dev dueño login seeded by Flyway (`V3__seed_dueno_dev.sql`): username `admin`, password `cambiame123`.
