# ADR 0018 — Configuration profiles: one global file plus dev/prod, tests on the test classpath

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 1
- **Source:** spec v2 section 10

## Context

`backend` had three configuration files, but not along clean lines. `application.yml` carried development values —
a localhost datasource and `${user.home}` as the allowed root — as if they were global. `application-prod.yml`
repeated settings it inherited. And `application-test.yml` sat in `src/main/resources`, which meant it shipped inside
the jar while **nothing ever activated the `test` profile**: no test, no Maven setting, no CI. It was dead
configuration that a reader would reasonably trust.

The other test file, `src/test/resources/application.yml`, was inert as well. A Spring Boot config-data trace on a
real run showed a single `application.yml` being imported
(`Imported 2 resources [class path resource [application.properties], class path resource [application.yml]]`), and
the effective application name was the one from `src/main` — not the `backend-test` that the test file declares. A
file named `application.yml` on both the main and the test classpath does not merge; one of them wins.

## Decision

- `application.yml` holds only what is identical in every environment: application name, port, JPA `validate`, the
  Liquibase changelog, actuator exposure and the repository limits.
- `application-dev.yml` holds every development default (localhost datasource, allowed roots, health details) and is
  the profile that applies when nothing is activated, via `spring.profiles.default: dev`.
- `application-prod.yml` lists only what differs from the global file and keeps no default for any credential or
  allowed root, so a production start without its environment variables still fails instead of falling back to
  localhost.
- Test configuration lives under `src/test/resources` and is activated in code (`@ActiveProfiles("test")`). It never
  ships, and it declares no datasource URL on purpose: a test that forgets Testcontainers fails on startup instead of
  quietly writing to the developer's own database.

## Consequences

- `./mvnw spring-boot:run` and running the jar need no profile; production must set `SPRING_PROFILES_ACTIVE=prod`.
- A production deployment that forgets that variable gets the development defaults rather than a hard failure at
  configuration time. In practice it fails on the first connection, because those credentials are not a real
  database's; the fail-fast path is reserved for the case where the profile *is* prod and the values are missing.
- The layout is guarded by a test, not by comments:
  `BackendApplicationTests#testProfileKeepsGlobalSettingsAndNeverTheDevelopmentDatabase` asserts which profile is
  active, that the global values are inherited, that `spring.datasource.url` stays unset and that the connection
  really comes from the container.
- A profile file with the same base name as another file in the same location is a trap; new configuration belongs in
  a profile-specific file, where the merge order is defined.- `analysis-worker` and `miner` inline their development defaults until they adopt the same split; see the
  amendment below, which is what happened in Phase 10.

## Amendment (2026-10-15): the split now covers every Spring app

`analysis-worker` and `miner` have adopted the layout, so it is the house rule for the whole workspace:

| Module | Common (every environment) | Local development (default) | Production |
|---|---|---|---|
| `backend` | `application.yml` | `application-dev.yml` | `application-prod.yml` |
| `analysis-worker` | `application.yml` | `application-dev.yml` | `application-prod.yml` |
| `miner` | `application.yml` | `application-dev.yml` | `application-prod.yml` |

- Each common file keeps only what is identical everywhere — application name, exposed actuator endpoints, Kafka
  serializer/deserializer and group settings, retry policy, repository limits — and sets
  `spring.profiles.default: dev`.
- Each dev file holds the localhost broker and datasource (the compose database on 5433, which compose publishes on
  every machine; see the comment on the `postgres` service), the clone directory and `show-details: always`.
- Each prod file states the broker, the database credentials and the clone directory **without defaults**, so a
  production start that is missing its environment fails instead of falling back to a developer machine's localhost;
  it also turns actuator details off and quiets logging.
- `miner/src/main/resources/application.properties` is **deleted**. It duplicated `application.yml`, and because
  `.properties` takes precedence over `.yaml` for the same base name, it silently shadowed it — the same class of bug
  this ADR records for a duplicate `application.yml`. No Spring configuration file in the workspace is
  `.properties` any more. The `.properties` files that remain are data, not configuration
  (`pmd/bcosmos-baseline.properties`, `eslint/bcosmos-baseline.properties`, `rules/rule-descriptions.properties`),
  and `RuleCatalogTest` pins the baseline ones to `docs/rule-catalog.md`.
- `backend/src/test/resources/application-test.yml` stays the only test-classpath profile; it is activated in code
  and never ships.
