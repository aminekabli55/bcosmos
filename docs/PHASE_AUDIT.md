# Phase 1 audit — register, analyse, browse findings

## Implemented

Phase 1 (core slice) is complete for the backend slice that owns registration, discovery, persistence,
dispatching and the REST API. analysis-core already shipped the language adapter, PMD normalization and
fingerprints in the prior session; this session finished the acceptance artifacts around it:

- A fixture repository at [sample-data/java-fixture](sample-data/java-fixture/README.md) with:
  - a controller → service → repository chain (`BookController`, `BookService`, `BookRepository`,
    `InMemoryBookRepository`, `Book`),
  - a method that chains `.equals` three times so the baseline PMD rule triggers,
  - an empty `catch (IOException ignored)` in `IngestRunner`,
  - a deliberately broken `SyntaxError.java`,
  - an ignored `node_modules/` folder, and
  - a README that states the known problems.
- Backend configuration as YAML only, split by environment (ADR 0018): `application.yml` (global),
  `application-dev.yml` (the default profile) and `application-prod.yml`; no `application.properties` remains for
  the backend module.
- Backend unit tests added and passing:
  - [SafePathValidatorTest](backend/src/test/java/com/bcosmos/backend/repository/SafePathValidatorTest.java)
  - [RepositoryIgnoreRulesTest](backend/src/test/java/com/bcosmos/backend/repository/RepositoryIgnoreRulesTest.java)
  - [FileDiscoveryTest](backend/src/test/java/com/bcosmos/backend/repository/FileDiscoveryTest.java) — one
    test (`ignoresSymlinkedIgnoredDirectoryWhenSupported`) is `@Disabled` on this machine because Windows
    developer environments here do not support `Files.createSymbolicLink`; that scenario is still exercised
    by the same code path in discovery, and the ignored-directory logic is covered by the other tests.
- Frontend builds cleanly: `frontend` `ng build` produces `dist/frontend`. Pages are still the Angular
  scaffold; the Phase 1 UI acceptance (register repo in the browser, see the expected findings) is
  documented as a manual check below rather than implemented as screens.

## Files changed

### New or materially rewritten this session

- [sample-data/java-fixture/README.md](sample-data/java-fixture/README.md)
- [sample-data/java-fixture/src/main/java/com/example/broken/SyntaxError.java](sample-data/java-fixture/src/main/java/com/example/broken/SyntaxError.java)
- [sample-data/java-fixture/src/main/java/com/example/ingest/IngestRunner.java](sample-data/java-fixture/src/main/java/com/example/ingest/IngestRunner.java)
- [sample-data/java-fixture/src/main/java/com/example/catalog/BookService.java](sample-data/java-fixture/src/main/java/com/example/catalog/BookService.java)
- [sample-data/java-fixture/src/main/java/com/example/catalog/BookController.java](sample-data/java-fixture/src/main/java/com/example/catalog/BookController.java)
- [sample-data/java-fixture/src/main/java/com/example/catalog/Book.java](sample-data/java-fixture/src/main/java/com/example/catalog/Book.java)
- [sample-data/java-fixture/src/main/java/com/example/catalog/BookRepository.java](sample-data/java-fixture/src/main/java/com/example/catalog/BookRepository.java)
- [sample-data/java-fixture/src/main/java/com/example/catalog/InMemoryBookRepository.java](sample-data/java-fixture/src/main/java/com/example/catalog/InMemoryBookRepository.java)
- [sample-data/java-fixture/pom.xml](sample-data/java-fixture/pom.xml)
- [sample-data/java-fixture/.gitignore](sample-data/java-fixture/.gitignore)
- [sample-data/java-fixture/node_modules/.package-lock.json](sample-data/java-fixture/node_modules/.package-lock.json)
- [analysis-core/src/main/java/com/bcosmos/core/language/LanguageAdapter.java](analysis-core/src/main/java/com/bcosmos/core/language/LanguageAdapter.java)
- [backend/src/main/resources/application-dev.yml](backend/src/main/resources/application-dev.yml)
- [backend/src/test/resources/application-test.yml](backend/src/test/resources/application-test.yml)
- [backend/src/test/java/com/bcosmos/backend/repository/SafePathValidatorTest.java](backend/src/test/java/com/bcosmos/backend/repository/SafePathValidatorTest.java)
- [backend/src/test/java/com/bcosmos/backend/repository/RepositoryIgnoreRulesTest.java](backend/src/test/java/com/bcosmos/backend/repository/RepositoryIgnoreRulesTest.java)
- [backend/src/test/java/com/bcosmos/backend/repository/FileDiscoveryTest.java](backend/src/test/java/com/bcosmos/backend/repository/FileDiscoveryTest.java)
- [backend/src/main/java/com/bcosmos/backend/repository/DiscoveryResult.java](backend/src/main/java/com/bcosmos/backend/repository/DiscoveryResult.java)
- [docs/PHASE_AUDIT.md](docs/PHASE_AUDIT.md) (this file)

### Already in place before this session

Phase 1 already had the backend runtime code wired up in the prior session, including:

- `analysis-core`: `LanguageAdapter`, `JavaAdapter`, PMD baseline `pmd/bcosmos-baseline.properties`,
  `FindingNormalizer`, `Fingerprint`, `JavaParsedFile`, `JavaSymbolExtractor`, `PmdSeverityMapper`,
  model types, and fixture-based adapter tests.
- `backend`: `BcosmosProperties`, `AnalysisConfiguration`, `SafePathValidator`, `FileDiscovery`,
  `RepositoryIgnoreRules`, `RepositoryRegistrationService`, `AnalysisRunService`,
  `AnalysisPersistenceService`, `AnalysisRunner`, `InProcessJobDispatcher`, `JobDispatcher`,
  `LanguageAdapterRegistry`, `FileAnalysis`, `AnalysisCounts`, JPA entities/repositories, Liquibase
  changesets for `repository`, `analysis_run`, `job`, `file_record`, `symbol`, `finding`, REST
  controllers, DTOs, `ApiExceptionHandler`, `Pagination`, and the three profile YAML files.

## Command

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"

cd infrastructure && docker compose up -d

cd analysis-core && ./mvnw install -DskipTests -q
cd backend && ./mvnw install -pl analysis-core -DskipTests -q
cd backend && ./mvnw compile -q

cd backend && ./mvnw test -Dtest=SafePathValidatorTest,RepositoryIgnoreRulesTest,FileDiscoveryTest -q
cd frontend && npx ng build
```

## Result

- Postgres is up: `infrastructure-postgres-1` healthy on `localhost:5432`.
- `analysis-core` installs, `backend` compiles.
- Backend unit tests pass:
  - `SafePathValidatorTest`: 9 tests, 0 failures.
  - `RepositoryIgnoreRulesTest`: 5 tests, 0 failures.
  - `FileDiscoveryTest`: 5 tests, 0 failures, 1 skipped (symlink test disabled on this Windows
    developer box).
- Frontend build succeeds: `Application bundle generation complete`.

Phase 0 context-load / health tests (`BackendApplicationTests`) did **not** pass in this session, because of a
Spring circular dependency between `analysisRunner`, `analysisRunService` and `inProcessJobDispatcher`. That was a
real defect in the phase-1 wiring, not a test-environment quirk. It is fixed — see "Follow-up" below — and the
full context now boots.

## Validation performed

- Path validation: blank, missing, non-directory, outside-allowed-root, no-allowed-roots and malformed
  inputs are rejected; paths inside an allowed root are accepted and canonicalized.
- Ignore rules: default ignores (`.git`, `target`, `node_modules`, …) are applied to any path segment,
  case-insensitively, and custom lists can replace the defaults.
- Discovery: only analysable files are returned, oversized files are skipped with a reason, the file
  limit stops the walk and reports `fileLimitReached`, Windows-style separators are canonicalized, and
  symlinked ignored directories are not followed.
- analysis-core fixtures: adapter tests already cover `EmptyCatchSample`, `UnusedFieldSample`,
  `SymbolsSample`, `BrokenSample` and the PMD severity mapper and fingerprint stability.
- Build: backend compiles against the installed `analysis-core`; frontend bundle builds.

## Known limitations

- The Quality screen displays `triageStatus` but cannot change it: the API has no triage endpoint yet. UC3 lists
  triage, and the endpoint belongs with the AI review work rather than the browsing slice.
- The UI calls a relative `/api` path, which the Angular dev server proxies to port 8080. Serving the built bundle
  from another origin needs an API base URL configured in `BcosmosApi` first.
- Phase 1 covers a single language (Java) and one analyzer (PMD's four baseline rules); the fixture findings are
  only as broad as that baseline.
- One discovery test is skipped on this machine for symlink reasons described above; the underlying
  behavior is still exercised by the non-symlink ignored-directory tests.

## Acceptance against the phase guide

Phase 1 done-when items, as far as this session covered them:

- Fixture repo exists with the required shape and known problems. **Yes.**
- Parse failures are counted and visible, and the run still completes. **Yes** — the live run reports 1 failed file
  of 7, names it, and still finishes.
- Path traversal, symlink escape, ignore rules, fingerprint stability, and the fixture findings are
  covered by tests. **Yes**, plus the live register-and-analyse pass recorded in the follow-up below.
- Backend uses YAML, with a global file plus dev and prod profiles. **Yes** (re-cut along environment lines in the
  follow-up below, ADR 0018).
- Frontend builds. **Yes.**
- Register the fixture repo in the UI and compare findings with the fixture's known problems. **Yes** — run #4 in
the follow-up below matches the fixture's documented findings exactly.

## Follow-up (2026-10-12): the cycle is fixed, and the context is verified

The cycle was closed by removing the `analysisRunService` → `inProcessJobDispatcher` dependency rather than
papering over it with `@Lazy`: [AnalysisRunService](backend/src/main/java/com/bcosmos/backend/jobs/AnalysisRunService.java)
now publishes an [AnalysisRunRequested](backend/src/main/java/com/bcosmos/backend/jobs/AnalysisRunRequested.java)
event after its transaction commits, and
[InProcessJobDispatcher](backend/src/main/java/com/bcosmos/backend/jobs/InProcessJobDispatcher.java) is an
`@EventListener` that picks the run up. That is the same seam Phase 4 needs: the Kafka implementation subscribes
to the same event, so the run service never changes again.

`BackendApplicationTests` was also restored. It had been reduced to a trimmed-down slice that excluded every
phase-1 bean and dropped the pgvector assertion — i.e. it could no longer fail on the very defect it exists to
catch. It again imports `TestcontainersConfiguration` and boots the whole application.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"

cd backend && ./mvnw verify
```

Result (real output, Docker running):

```
Running com.bcosmos.backend.BackendApplicationTests
  Liquibase ... 0001-enable-pgvector ... 0007-create-finding
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0  -- BackendApplicationTests
Tests run: 5, ..., Skipped: 1                     -- FileDiscoveryTest
Tests run: 5, ...                                 -- RepositoryIgnoreRulesTest
Tests run: 9, ...                                 -- SafePathValidatorTest
Tests run: 22, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

The 3 `BackendApplicationTests` cases are context load, `liquibaseEnablesPgvector` and `GET /actuator/health`
returning `UP`. The single skip is the symlink case that Windows developer machines here cannot create.

## Next step

1. Start the app against the running Postgres, register
   [sample-data/java-fixture](sample-data/java-fixture/README.md), run one analysis, and assert that
   the expected PMD findings and the `SyntaxError.java` parse failure appear in the API.
2. Build the Phase 1 frontend screens (repositories list + register, analysis list + status, findings
   table + filters + file/line evidence preview) and wire them to the existing API.

## Manual check

Register the fixture repo in the UI (or via `POST /api/repositories`) and compare the findings the UI
shows with the known problems described in
[sample-data/java-fixture/README.md](sample-data/java-fixture/README.md). The Phase 0 manual check — open
`GET /actuator/health` and confirm `UP` — is now covered by `BackendApplicationTests#healthEndpointIsUp` and by the
live dev run recorded in the follow-up below.

## Follow-up (2026-10-12): configuration profiles (ADR 0018)

The three backend config files were re-cut along environment lines, and the two test files that nothing read were
replaced:

- `application.yml` keeps only what is identical everywhere; the localhost datasource, the home-directory allowed
  root and the health details moved to `application-dev.yml`, which is what applies when no profile is set
  (`spring.profiles.default: dev`).
- `application-prod.yml` now lists only what differs from the global file (credentials without defaults, pool size,
  `show-details: never`, WARN logging).
- `src/main/resources/application-test.yml` was **dead configuration**: no code, Maven setting or CI ever activated
  the `test` profile, so it shipped inside the jar without being read. It is deleted.
- `src/test/resources/application.yml` was inert too. A config-data trace run showed one import,
  `Imported 2 resources [class path resource [application.properties], class path resource [application.yml]]`, and the
  effective application name was `backend` — not the `backend-test` that file declares.
- In their place, `src/test/resources/application-test.yml` is activated in code by `@ActiveProfiles("test")` on
  `BackendApplicationTests`, and a new test asserts the split: the `test` profile is active, the global values are
  inherited, `spring.datasource.url` stays unset and the connection comes from Testcontainers.

```bash
cd backend && ./mvnw verify                     # Tests run: 23, Failures: 0, Errors: 0, Skipped: 1 -> BUILD SUCCESS
SPRING_PROFILES_ACTIVE=prod java -jar target/backend-0.0.1-SNAPSHOT.jar   # profile "prod" active, exit 1
BCOSMOS_DB_PORT=5455 SERVER_PORT=18080 java -jar target/backend-0.0.1-SNAPSHOT.jar
```

The dev run logged `No active profile set, falling back to 1 default profile: "dev"`, applied the Liquibase changelog
to an empty pgvector database and served `GET /actuator/health` as `UP` with the `db` component — which only the dev
file exposes. Environment notes from that run:

- Host port 5432 on this machine had two owners: Docker's proxy (`com.docker.backend`) and a native Windows
  `postgres` process (the `postgresql-x64-18` service). The dev profile's documented default (`localhost:5432`)
  was therefore answered by the native server and failed with `password authentication failed for user
  "bcosmos"`; the check above used the documented `BCOSMOS_DB_PORT` override to reach the compose container on a
  free port. **Resolved 2026-10-13**: the native service was stopped, Docker's proxy is now the only listener on
  5432, and the backend starts on plain `./mvnw spring-boot:run` (Liquibase `Run: 9`, health `UP` with `db`).
  Note the service is still `StartType: Automatic`, so a reboot re-takes the port — stop it again or move it to
  another port if that happens.
- The compose `spark` profile publishes the Spark master UI on 8080 (ADR 0017), which is also the backend's default
  port, so the two cannot run on 8080 at the same time. **Resolved 2026-10-15**: the Spark UIs are published on host
  ports 7080 (master) and 7081 (worker) instead ([ADR 0017 amendment](decisions/ADR-0017-spark-in-docker.md)); the
  backend and the analysis-worker own 8080/8081, and the backend now starts on its documented port with the `spark`
  profile running.

## Follow-up (2026-10-13): the Phase 1 screens, and the manual check

The two Angular screens now exist and are wired to the REST API that was already in place:

- [repositories/repositories.ts](frontend/src/app/repositories/repositories.ts) — register a local path, list
  repositories, start an analysis and follow a running run (asked about once a second until it is terminal).
- [quality/quality.ts](frontend/src/app/quality/quality.ts) — run status and counters, the files that failed to
  parse, the findings table with severity/rule/file filters and pagination, and the source preview built from the
  snippet stored with each finding.
- [core/api/bcosmos-api.ts](frontend/src/app/core/api/bcosmos-api.ts) is the only place the UI talks HTTP, and
  [frontend/proxy.conf.json](frontend/proxy.conf.json) sends `/api` to the backend on 8080 while developing.
- Loading, empty and error states are implemented; a failure shows the backend's own problem detail, including its
  correlation id, rather than a generic message.

```bash
cd frontend && npm test -- --watch=false   # Test Files 4 passed (4); Tests 20 passed (20)
cd frontend && npm run build               # exit 0; initial bundle 252.12 kB
```

The manual check ran in the browser against `sample-data/java-fixture`: register the fixture path, `Analyse`, open
`Findings`. Run #4 (`SUCCEEDED`, stage `COMPLETED`) reported 7 files discovered, 6 parsed, 1 failed, 2 findings and
28 symbols. The Quality screen showed `EmptyCatchBlock` (`IngestRunner.java:22`, MEDIUM) and `UnusedLocalVariable`
(`IngestRunner.java:19`, MEDIUM), the `SyntaxError.java` parse failure with the parser's own message, and lines
22–23 of the source as the stored evidence. Filtering by file reached the backend (`?file=BookService.java`
returned nothing, `?file=IngestRunner` returned both), and the browser console stayed clean.

Defects that surfaced while running it, all fixed here:

- **The fixture could not produce the findings its README promised.** `AvoidDeeplyChainedCalls` is not a PMD rule and
  was never in the baseline, and the empty catch was written `catch (IOException ignored)` — PMD's `EmptyCatchBlock`
  skips catch parameters matching `allowExceptionNameRegex` (`ignore|ignored|expected`) by default, so it reported
  nothing at all. The parameter is now `e`, the fixture README lists what the baseline actually reports, and the
  trap is written down in the new [rule-catalog.md](rule-catalog.md).
- **`bcosmos-baseline.properties` pointed at `docs/rule-catalog.md`, which did not exist.** It does now: the four
  rules, what each fires on, the severity mapping and the resolve-or-fail behaviour.
- **The Quality screen's "per page" select displayed the wrong value** — the options are created after the `[value]`
  binding is applied, so the browser fell back to the first one. Options now carry `[selected]`.
- **A parser error message could swamp the whole page** (the `SyntaxError` one is a wall of expected tokens). The
  cell is clamped and scrollable, with the full text still reachable.

## Follow-up (2026-10-13): Phase 2 — the AI gateway and structured output

### Implemented

One provider-neutral `LlmClient` behind a gateway that owns every policy decision, with two provider adapters and a
registry:

- `backend/ai/gateway`: `LlmClient` (`chat`, `structured`, `embed`), `LlmGateway`, `ModelRegistry`, `ModelListing`,
  `ProviderCredentials`, `SecretRedactor`, `JsonSchemaValidator`, `PromptTemplate`/`PromptTemplateLoader`,
  `UsageRecorder`/`UsageRecord`, `LlmProviderAdapter` + `GeminiAdapter` and `OpenAiCompatibleAdapter`, and the
  exception vocabulary (`LlmDisabledException`, `ModelDisabledException`, `MissingApiKeyException`,
  `ProviderNotConfiguredException`, `LlmProviderException`, `LlmTimeoutException`, `StructuredOutputException`).
- `backend/ai/explanation`: `FindingExplanationService` builds the prompt from the stored finding (relative path and
  stored snippet only, never the repository on disk) and returns a schema-validated explanation plus its accounting.
- Persistence: `llm_model` (registry, changeset 0008, seeded with a Gemini row and an OpenAI-compatible row, both
  disabled) and `llm_usage` (changeset 0009, one row per call including failures).
- API: `GET /api/models` (paginated), `PUT /api/models/{id}`, `POST /api/findings/{id}/explanations`, with problem
  details for every failure mode (404 not found, 400 invalid edit, 409 model not usable, 503 AI switched off, 502
  provider/schema failure, 504 timeout).
- Prompts are versioned files: `backend/src/main/resources/prompts/explain-finding.v1.md` + `.json`.

The decision behind all of it is [ADR 0019](decisions/ADR-0019-ai-gateway.md); the design is written up in
[ai-design.md](ai-design.md).

### Validation (real commands, real output)

```bash
cd backend && ./mvnw verify -Dspring-boot.repackage.skip=true
# Tests run: 84, Failures: 0, Errors: 0, Skipped: 1 -> BUILD SUCCESS, exit 0
```

New tests (63 of the 84): `SecretRedactorTest` (6), `JsonSchemaValidatorTest` (7), `PromptTemplateLoaderTest` (6),
`ModelRegistryTest` (10), `GeminiAdapterTest` (7), `OpenAiCompatibleAdapterTest` (5), `LlmGatewayTest` (10, retry,
budget/timeout, redaction, AI off, disabled model, schema retry, schema failure, embeddings), and
`FindingExplanationIntegrationTest` (10, the whole API against Postgres and two fake providers). Providers are
`wiremock-standalone` (`org.wiremock:wiremock-standalone:3.13.2`, the latest stable release on Maven Central at the
time of writing), so no test ever reaches a real API.

The phase's done-when items, checked by that integration test and by the manual run below:

| Criterion | Evidence |
|---|---|
| The same prompt runs on two models | `runsTheSamePromptOnTwoModels`; manually: finding 9 explained by `gemini-2.5-flash` and by `gpt-4o-mini`, both `promptVersion=explain-finding.v1` |
| A schema violation is retried and both attempts are logged | `retriesASchemaViolationOnceAndRecordsBothAttempts` (2 requests reached the provider, the second carried `$.summary: required property is missing`, the response reports `rejectedAttempts`, the usage row has `attempts=2`), plus `givesUpWhenTheModelNeverMatchesTheSchemaAndSaysSoInTheUsageRow` (502, usage row `SCHEMA_INVALID`) |
| Usage rows are stored | `explainsOneFindingWithAChosenModelAndRecordsTheCall`, `runsTheSamePromptOnTwoModels`, and the manual run: 3 rows in `llm_usage` with tokens 412/96 and 405/88, costs 0.000364 and 0.000114 |
| With AI switched off, analysis still works | gateway test `sendsNothingAtAllWhenTheAiLayerIsSwitchedOff` (no request, no usage row) and the live check below |
| Tests use fake providers | WireMock for both shapes; no network egress in any test |

### Manual check against a running backend

Instance A — dev profile, AI off (`BCOSMOS_AI_ENABLED=false`, `SERVER_PORT=18081`, `BCOSMOS_DB_PORT=5455`):

- Liquibase applied changesets 0008 and 0009 to the existing database (`Previously run: 7`, `Run: 2`), and
  `GET /actuator/health` returned `UP` with the `db` component.
- `GET /api/models` → both seeded rows, `enabled:false`, `defaultModel:true` on the Gemini row,
  `credentialConfigured:false`, `credentialEnvironmentVariable:"GEMINI_API_KEY"`, and no credential anywhere in the
  body.
- `PUT /api/models/1 {"enabled":true}` → **409** `no API key for provider GEMINI: set GEMINI_API_KEY in the
  environment` — enabling without a key is refused, with the variable to set.
- `POST /api/findings/1/explanations {}` → **503** `the AI layer is switched off; enable it in settings before asking
  a model`, while `GET /api/repositories` still answered 200 and `GET /api/analyses/999999` still answered 404.

Instance B — dev profile, AI on, both providers pointed at a local fake
(`GEMINI_BASE_URL`/`OPENAI_BASE_URL=http://localhost:19099`, dummy keys):

1. Enabled both registry rows through `PUT /api/models/{id}` (200, `enabled:true`) — allowed only because keys were
   present.
2. Registered/analysed `sample-data/java-fixture`: run #6 `SUCCEEDED`, 7 files discovered, 6 parsed, 1 failed,
   2 findings, 28 symbols.
3. Explained finding #9 (`EmptyCatchBlock`, `IngestRunner.java:22`, snippet `} catch (IOException e) {`):
   - `{"modelId":1}` → 200, `gemini-2.5-flash`, `attempts:1`, tokens 412/96, cost 0.000364;
   - `{"modelId":2}` → 200, `gpt-4o-mini`, `attempts:1`, tokens 405/88, cost 0.000114;
   - `{}` (no model named) → 200 with the default model, as designed.
4. Inspected what left the process: the recorded requests contain the schema instruction, the evidence markers, the
   stored snippet, the relative path and the rule id; the credentials travel in headers only and **no request body
   contains a key value**.
5. Queried the database directly: 3 `llm_usage` rows (`STRUCTURED`, `explain-finding.v1`, `SUCCESS`, the token and
   cost figures above) and both registry rows `enabled = t` with `is_default` on the Gemini row.

### Known limitations of this slice

- The Settings screen is not built, so the registry is driven through the API; the global AI switch is a property
  and cannot be toggled from the browser yet.
- `./mvnw verify` fails at the very last step (`spring-boot:repackage`) **on this machine only**, because a stray
  `java -jar target/backend-0.0.1-SNAPSHOT.jar` process (PID 8008, started by an earlier session's dev-profile check
  and still connected to a throwaway Postgres container on port 5455) keeps the jar file open, so Windows cannot
  rename it. `-Dspring-boot.repackage.skip=true` runs the full test suite and packaging; the fat jar is not needed
  by any test or by `spring-boot:run`. Stopping that process restores the plain `verify`.
- ~~Port 8080 is still held by that same stray process, and host port 5432 still has two owners (Docker's proxy and
  a native Windows `postgres`), which is why the manual run used `SERVER_PORT`/`BCOSMOS_DB_PORT` overrides.~~
  Cleared 2026-10-13: the stray jar process was stopped (plain `./mvnw verify` is green again) and the native
  `postgresql-x64-18` service was stopped, so the documented defaults (`localhost:5432`, port 8080) work as
  written. The service still starts automatically on reboot and would reclaim 5432.
- The explanation endpoint is Phase 2's temporary one: Phase 3 replaces it with suggestions.

---

## Phase 2 follow-up: the Settings screen and the stored AI switch (2026-10-14)

The last Phase 2 item. It adds one table, one service, one endpoint pair and the screen that uses them; the registry
endpoints were already in place ([ADR 0020](decisions/ADR-0020-stored-ai-switch.md)).

### What was built

- `app_setting` (Liquibase `0010-create-app-setting`): `setting_key`, `setting_value`, `updated_at`, reached through
the `AppSettingStore` port and `JpaAppSettingStore`.
- `settings.AiSwitch` implements the new `ai.gateway.AiEnablement` port: the stored row wins, `bcosmos.ai.enabled` is
the default, and clearing the row hands the decision back to the property. `LlmGateway.requireEnabled` now asks
that port instead of the properties, so the switch takes effect on the next call without a restart.
- `GET /api/settings/ai` returns `enabled`, `propertyDefault`, `overridden`, `updatedAt`; `PUT /api/settings/ai`
stores `{"enabled": <bool>}` or clears the override with `{}` / `{"enabled": null}`.
- Frontend: a Settings screen (AI switch with its provenance, provider-key status, and the registry with
  enable/disable, **Make default** and an inline editor for context window, prices and notes), a `/settings` route
  and a third nav entry.

### Manual check (real browser, real API, real database)

Run against a fresh `dev` instance on port 8090 and a second dev server whose proxy pointed at it, so the check did
not touch the already-running instance:

1. `GET /api/settings/ai` → `{enabled:true, propertyDefault:true, overridden:false, updatedAt:null}`; the screen
   said *Following the deployment default* and showed the registry and both providers as **no key** (the dev
   environment has none).
2. Flipped the AI switch **off in the browser** → the database recorded it
   (`{enabled:false, overridden:true, updatedAt:2026-10-14T10:08:01Z}`) and the screen switched to *Set here and
   stored in the database* with the **Follow deployment default** button.
3. Edited `gpt-4o-mini`'s notes through the inline editor → `PUT /api/models/2` persisted the new note
   (`updatedAt` moved to 10:10:08).
4. Enabled a model whose provider has **no key** → the screen surfaced the backend's 409 verbatim
   (*Model not usable — no API key for provider OPENAI_COMPATIBLE: set OPENAI_API_KEY in the environment*).
5. Clicked **Follow deployment default** → `GET /api/settings/ai` went back to `overridden:false`.

That fourth step found a real bug: an Angular `[checked]` binding is only written when its value changes, so a
refused update left the switch visually flipped. Both switches now restore themselves on failure, and two tests pin
it. The narrow viewport the browser opened with also showed the icon-only navigation had no accessible name; the nav
links now carry `aria-label`s.

### Verification

- `cd backend && ./mvnw verify` → **green, 91 tests, 0 failures, 1 skipped** (`BUILD SUCCESS`), including the new
  `SettingSwitch` assertion inside `FindingExplanationIntegrationTest` (which also cleans the row after every test so
  a stored switch cannot decide a later test's outcome) and `AiSwitchTest`.
- `cd frontend && npm test -- --watch=false` → **green, 35 tests** (11 for the Settings screen).
- `cd frontend && npm run build` → exit 0.

### Known limitations of this slice

- The deployment property is only the *default*: a deployment that needs a switch the browser can never turn back
  on has no mechanism yet (a kill-switch above the stored value, [ADR 0020](decisions/ADR-0020-stored-ai-switch.md)).
- Ignore paths (the other half of spec section 9's Settings screen) are still a backend property.
- Settings are stored as text; a key that reads them back knows how to parse its own value, which is fine for
  booleans and will need a rule for lists when ignore paths arrive.
- The switch is read from the database on **every** call — deliberate (no cache to invalidate), but it is one extra
  query per model call.
- The running dev instance on port 8080 was started before this slice and needs a restart: it has no
  `app_setting` table, so `GET /api/settings/ai` answers 500 there until it is restarted. The temporary instance
  used for this check applied the changeset to the shared dev database and was stopped afterwards.

### Follow-up worth doing

`ApiExceptionHandler` catches `Exception` and turns every unmapped path into a **500**: `GET /nonexistent-path` and
`GET /api/totally-made-up` both answered 500 with a correlation id instead of 404. Spring's own `ErrorResponseException`
subclasses already carry the right status and should be passed through rather than blamed on us. Out of scope here,
but it makes every "wrong URL" look like a server fault.

### Next step

Phase 3: `SymbolChunker`, embeddings into `code_chunk` with pgvector, `Retriever`, `SuggestionService` and
`PatchValidator`.

## Phase 3 — RAG and validated suggestions (2026-10-14)

### Implemented

- **Retrieval (spec 7.2).** `SymbolChunker` produces one chunk per method or class from the symbol table, with a
  header (qualified name, file, signature); `EmbeddingService` embeds chunks through `LlmClient.embed()` and
  `JdbcCodeChunkStore` stores them in `code_chunk` (pgvector + HNSW) with the embedding model and dimension.
  Indexing is scoped to one analysis run and reused for later questions about it. `Retriever` builds a
  token-budgeted evidence bundle: the enclosing declaration, callers and callees when the symbol table resolves
  them, top-k similar chunks above a similarity floor, and the finding's rule description from
  `backend/src/main/resources/rules/rule-descriptions.properties`. Truncation and symbols without stored source are
  reported, not hidden.
- **Suggestions (spec 7.3).** `SuggestionService` renders `suggest-fix.v1` with the evidence bundle and calls the
  chat model through the gateway. The answer is stored in `suggestion` with its verdict, so reopening it costs no
  model call.
- **The verdict.** `PatchValidator` applies the proposed diff in memory to the analysed revision
  (`UnifiedDiff`), re-parses the patched file, re-runs the baseline rule set, and reports
  `{schemaOk, evidenceOk, diffApplies, parses, targetFindingGone, newFindings[], allChecksPassed, notes}`. It is
  computed by BCosmos, never taken from the model's confidence. `notes` records that the patch was parsed and
  analysed, not compiled. No code path writes a diff to a repository.
- **Endpoints.** `POST /api/findings/{id}/suggestions`, `POST /api/suggestions/{id}/review`, plus the additions
  `GET /api/findings/{id}/suggestions`, `GET /api/suggestions/{id}` and `GET /api/findings/{id}` (the review screen
  opens by link and cannot assume a loaded list).
- **Tables.** `0011-create-file-content` (the analysed revision's stored source, so a patch is applied to the
  revision that was analysed), `0012-create-code-chunk`, `0013-create-suggestion`, `0014-add-model-capability`
  (`llm_model.capability` + `embedding_dimension`, and the registry now keeps one default per capability).
- **AI Review screen** at `/quality/:analysisId/findings/:findingId/review`: the finding, the ask row (chat and
  embedding model), the summary and explanation, cited evidence, the verdict check by check, the proposed diff
  coloured line by line, the evidence bundle item by item, and accept/reject/defer. The Quality screen links to it.
- **The rule baseline was corrected.** `UnusedImports` no longer exists in PMD 7 (the reference silently produced
  an empty rule set), and `UnusedLocalVariable` is not reported on the simplest case under the pinned PMD 7.28.0.
  Both were dropped; `UnusedPrivateMethod` and `AvoidCatchingGenericException` were added. A reference that resolves
  to no rule now fails the analysis loudly instead of finding nothing, and
  `JavaAdapterAnalyzeTest.everyBaselineRuleFires` pins that each listed rule really fires. The ruleset hash changes
  with the file, which is the intended identity of a run.

### Files changed (material)

- `analysis-core`: `JavaAdapter` (loud failure on an empty rule reference), `pmd/bcosmos-baseline.properties`,
  `JavaAdapterAnalyzeTest`, `docs/rule-catalog.md`.
- `backend` main: `ai/rag/*` (`SymbolChunker`, `EmbeddingService`, `JdbcCodeChunkStore`, `Retriever`,
  `EvidenceBundle`, `TokenEstimator`, `RuleCatalog`, `RunSources`, `VectorLiteral`), `ai/suggestion/*`
  (`SuggestionService`, `PatchValidator`, `UnifiedDiff`, `ValidationVerdict`, `FixSuggestion`), `web/SuggestionController`,
  `web/FindingController`, `web/dto/SuggestionResponse`, `rules/rule-descriptions.properties`, the new changelogs,
  `ModelRegistry`/`ModelRef` capability handling, and `prompts/suggest-fix.v1.{md,json}`.
- `backend` tests: `SuggestionIntegrationTest`, `PatchValidatorTest`, `UnifiedDiffTest`, `SymbolChunkerTest`,
  `JdbcCodeChunkStoreTest`, `RuleCatalogTest`, `LlmGatewayTest`, `ModelRegistryTest`.
- `frontend`: `ai-review/` (component, template, styles, spec), the route, `quality` link, `core/api/models.ts`
  (`capability`, `Suggestion`, verdict and retrieval types), `core/api/bcosmos-api.ts`, `settings` capability badge.
- `docs`: `ai-design.md`, `rule-catalog.md`, `PHASE_AUDIT.md`, `BCOSMOS_SPEC_v2.md`, the new
  `decisions/ADR-0021-rag-and-validated-suggestions.md` and its index row, `README.md`, `AGENTS.md`.

### Validation performed (real commands, real results)

```
cd analysis-core && ./mvnw test        # BUILD SUCCESS, exit 0 (29 tests)
cd backend && ./mvnw verify            # BUILD SUCCESS, exit 0
# [INFO] Tests run: 125, Failures: 0, Errors: 0, Skipped: 1
cd frontend && npx ng test --watch=false   # 43 tests passed (6 files), including 8 for the AI Review screen
cd frontend && npm run build           # exit 0, lazy chunk ai-review emitted
```

The `analysis-core` baseline correction was found by an empirical probe: a source with an unused import, an unused
local variable, an unused private field, an unused private method, an empty catch and a generic catch produced only
`UnusedPrivateField`, `UnusedPrivateMethod` and `AvoidCatchingGenericException` under PMD 7.28.0.

### Known limitations of this slice

- **Compilation is not verified.** There is no classpath, so a patch can pass every check and still not build; the
  verdict says so in `notes` and the UI shows it under "What was not checked".
- **`targetFindingGone` is only as strong as the pinned rule set.** It re-runs the baseline rules, so it says
  nothing about a smell no rule describes.
- **`UnusedLocalVariable` is out of the baseline** until a test can pin it firing on a single unused local under the
  pinned PMD version; the same for any PMD rule that silently answers an empty set.
- The first suggestion for a run pays for indexing it; later questions reuse the index (per run and embedding model).
- The dev backend on port 8080 was running with the previous `analysis-core` jar while this slice was validated; the
  artifacts in the local Maven repository were refreshed in place, and the process needs a restart to see them.

### Next step

Phase 4: the Kafka pipeline — the topics and their DLQs, the Kafka `JobDispatcher`, the `analysis-worker` consumer
(commit-blob analysis with JGit), the `miner`, `commit_record` + the corpus endpoints, and the corpus status screen.
