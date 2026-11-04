# BCosmos

BCosmos turns repositories (Java first) into an evidence-backed map of architecture and quality, reviews every new
commit, mines open-source history at scale, and uses LLMs — evaluated and fine-tuned on that data — to explain and
fix findings.

It is a **lab for AI code review on top of static analysis**, not a SonarQube replacement: PMD finds the problems,
the LLM explains and proposes fixes, and BCosmos measures how good those fixes are.

- **New here? Start with [docs/using-bcosmos.md](docs/using-bcosmos.md)** — what each part is, what to start, what
to type in each field, what every screen shows, and how to fix the common startup failures.
- Specification: [docs/BCOSMOS_SPEC_v2.md](docs/BCOSMOS_SPEC_v2.md) (authoritative)
- Phase guide: [docs/BCOSMOS_PHASES.md](docs/BCOSMOS_PHASES.md)
- Decisions: [docs/decisions/](docs/decisions/README.md)

## Current status

**Phase 0 (foundation) and Phase 1 (register a local Java repository, analyse it, browse findings) are complete.**
Registration, discovery, persistence, dispatching, the REST API and the Repositories and Quality screens are in
place; `cd backend && ./mvnw verify` is green (91 tests, 0 failures, 1 skipped) and
`cd frontend && npm test -- --watch=false` is green.

**Phase 2 (AI gateway and structured output) is complete.** One provider-neutral `LlmClient`, a Gemini adapter and
an OpenAI-compatible adapter, the `llm_model` registry and its API, usage recording, prompt redaction,
JSON-schema-validated answers with a single retry, and `POST /api/findings/{id}/explanations`. The Settings screen
drives the registry and the AI master switch from the browser: `GET/PUT /api/models` and `GET/PUT /api/settings/ai`,
where the stored switch overrides the `bcosmos.ai.enabled` deployment default
([ADR 0020](docs/decisions/ADR-0020-stored-ai-switch.md), [docs/ai-design.md](docs/ai-design.md)).

**Phase 3 (RAG and suggestions) is complete.** A finding can now be sent to a chat model together with a retrieved
evidence bundle (enclosing declaration, callers and callees, similar chunks by pgvector distance, and the baseline
rule's own description), and the answer comes back with a verdict BCosmos computes itself: schema, evidence,
whether the diff applies in memory, whether the patched file parses, whether the finding is gone, and what new
findings the patch would introduce. Accept, reject and defer a suggestion from the AI Review screen; a patch is
never applied. See [ADR 0021](docs/decisions/ADR-0021-rag-and-validated-suggestions.md).

**Phase 4 (Kafka pipeline and corpus ingestion) is complete.** One `CommitAnalysisPipeline` plus a `FileSource` port
in `analysis-core`, shared by the backend and `analysis-worker` ([ADR 0025](docs/decisions/ADR-0025-shared-pipeline-port.md));
`AnalysisRequestedConsumer` and `CommitDiscoveredConsumer` with idempotent sinks keyed on
`(repository_id, commit_hash, file_path, side, analyzer_version, ruleset_hash)`
([ADR 0027](docs/decisions/ADR-0027-idempotent-upsert-shape.md)) and DLQ handling with bounded exponential backoff
([ADR 0026](docs/decisions/ADR-0026-dlq-semantics.md)); the `miner` clones allowlisted public repositories over
HTTPS with JGit, no checkout, and walks their commits; the backend refuses on-demand runs for MINED repositories
with HTTP 422 ([ADR 0024](docs/decisions/ADR-0024-mined-repo-analysis-policy.md)); the `/corpus` screen drives
ingestion and shows its status. Measured pipeline numbers and the named dataset are in
[docs/benchmarks.md](docs/benchmarks.md).

**Phase 5 (Spark lakehouse and fix-triples) is complete.** The medallion layout
([ADR 0004](docs/decisions/ADR-0004-spark-analytics.md)) in `spark-jobs`: `streaming/findings_to_bronze.py` (Kafka
or a directory of events → partitioned Parquet bronze, checkpointed) and the batch chain `silver_clean` →
`rule_distribution` → `quality_trends` → `time_to_fix` → `hotspots` → `fix_triples` → `load_gold`, a versioned
fix-triple dataset with its generated [dataset card](docs/dataset-card.md), and the `agg_*` gold tables the backend
reads ([ADR 0028](docs/decisions/ADR-0028-spark-gold-layer-and-analytics-api.md)). `GET /api/analytics/*` exposes
them and the `/analytics` screen draws them with Chart.js, every chart drilling down to the records behind it.

**Phase 6 (evaluation harness and benchmarking) is complete.** The `benchmark_run` table (changeset 0019),
`GET/POST /api/benchmarks`, the Python harness in [ai-lab/eval](ai-lab/eval) computing the seven metrics on a
held-out split, `POST /api/findings/{id}/compare-models`, and the sortable `/benchmarks` leaderboard with a
dataset-version filter and stated sample sizes.

**Phase 7 (fine-tuning) is complete.** QLoRA dataset preparation CLI (`prepare_dataset.py`) converting fix-triples into chat-formatted SFT JSONL rows; Colab/Kaggle notebook generator and notebook (`finetune_qlora.ipynb`) using Hugging Face `transformers`, `peft` and `trl.SFTTrainer` (4-bit NF4); evaluation runner (`evaluate_finetuned.py`) measuring all 7 benchmark metrics; adapter merge utility (`merge_and_push.py`); Liquibase changeset registering `bcosmos-qwen2.5-coder-1.5b-qlora` (kind `FINETUNED`) in `llm_model`; [docs/fine-tuning-report.md](docs/fine-tuning-report.md) with measured numbers; and [ADR 0030](docs/decisions/ADR-0030-qlora-fine-tuning-and-evaluation.md).

**Phase 8 (Graph, history, polish) is complete.**
- Call graph & dependency extraction in `analysis-core` (`CALLS`, `EXTENDS`, `IMPLEMENTS`, `USES_TYPE`) persisted in `relationship` table via changeset `0021-create-relationship.yaml`.
- Bounded Graph API with server-side traversal limits (`GET /api/analyses/{id}/graph`, `GET /api/symbols/{id}/impact`, `GET /api/analyses/{id}/hotspots`).
- Snapshot comparison & finding transitions (`GET /api/analyses/compare?from=&to=`), persisted in `finding_transition` table via changeset `0022-create-finding-transition.yaml`. Strict invariant safeguards (spec §4.2): unresolved findings on failed parses or disabled rules are marked `UNCERTAIN`.
- Code Cosmos screen (`/cosmos`): 2D interactive Cytoscape.js canvas, search, package clustering, node side-drawer with blast radius / impact analysis, top hotspots ranking with visible formula (`score = (findings * 10) + (highSeverity * 20) + (inDegree * 5) + (lineCount * 0.05)`), and accessible table fallback.
- History screen (`/history`): snapshot delta comparison with `NEW`, `RESOLVED`, `PERSISTENT`, `UNCERTAIN` stat cards, filter tabs, and transition explanations.
- See [ADR 0031](docs/decisions/ADR-0031-code-cosmos-graph-and-snapshot-history.md).

**Phase 9 (Commit Review) is complete.** A three-layer, evidence-based review of a commit
([ADR 0032](docs/decisions/ADR-0032-commit-review.md)): facts read from the JGit diff and from the run-to-run
finding transitions, explainable risk heuristics **labelled as heuristics**, and an optional AI summary whose every
cited file is verified against the diff. `commit_review` (changeset `0023-create-commit-review.yaml`) backs
`GET /api/repositories/{id}/commits`, `POST /api/repositories/{id}/commit-reviews` and
`GET /api/commit-reviews/{id}`, and the `/commit-reviews` screen picks a commit and shows the result. With AI off,
the facts and the risk signals still show.

**Phase 10 (second language, TypeScript) is complete.** `TypeScriptAdapter` (Tree-sitter 0.25.3 plus an ESLint rule
baseline: `no-empty`, `no-unused-vars`, `no-explicit-any`, `no-console`) at support level 2 — symbols, findings and
AI explanation, no full call graph — passing the same adapter contract suite as `JavaAdapter`; the pipeline and the
worker route by language without touching Kafka, Spark, the AI gateway or the schema;
[sample-data/angular-fixture](sample-data/angular-fixture/README.md) proves it end to end; and the UI shows the
support level per language (`Java · L3`, `TypeScript · L2`)
([ADR 0033](docs/decisions/ADR-0033-typescript-adapter.md)).

**Repository deletion (post-Phase 10, [ADR 0037](docs/decisions/ADR-0037-repository-deletion.md)) is complete.**
Registration was one-way until now: the API had no `DELETE` and re-registering a known path is refused, so a
wrongly registered directory could only be removed with hand-written SQL. `DELETE /api/repositories/{id}` now
forgets a registration **and everything derived from it** (runs, files, symbols, findings, suggestions, chunks,
relationships, transitions, commit records, commit reviews) in a single statement, cascaded by PostgreSQL; changeset
`0024-commit-review-repository-cascade.yaml` gives `fk_commit_review_repository` the `ON DELETE CASCADE` that every
other repository foreign key already had and that used to make the delete fail. The request answers 404 for an
unknown id and 409 while one of the repository's runs is still `PENDING` or `RUNNING`; the folder on disk is never
touched. The Repositories screen deletes behind an inline confirmation and shows the backend's own message when it
refuses.

**Cosmic interface (UI pass, [ADR 0042](docs/decisions/ADR-0042-cosmic-design-system.md)).** The frontend now has one
design system instead of two half-defined palettes: `styles.scss` owns the tokens (and aliases the legacy
`--surface-1` / `--text-primary` / `--bg-surface` names that Benchmarks and Commit Review were silently failing to
resolve), the shell is a grouped left rail over a starfield/nebula/moon backdrop, and Repositories opens on a hero
with a ringed planet and live counts. Space Grotesk, Inter and JetBrains Mono split display, prose and code; glass
cards, gradient hairlines, table hover sweeps and a small named motion vocabulary (page rise, aurora sweep, orbit,
float, twinkle, button sheen) carry the theme, all disabled under `prefers-reduced-motion`. `cd frontend && npm test
-- --watch=false` is green (**117 tests**, 14 files) and `npm run build` exits 0 with zero budget warnings
(initial 320.82 kB raw / 88.2 kB transferred).

**All ten phases of the roadmap are complete.** The per-phase delivered components and the real verification
numbers are recorded in [docs/AGENT_HANDOFF.md](docs/AGENT_HANDOFF.md) and in the "Current status" section of
[docs/BCOSMOS_SPEC_v2.md](docs/BCOSMOS_SPEC_v2.md).

## Layout

| Folder | What it is | Local port |
|---|---|---|
| `backend` | Spring Boot modular monolith: REST API, persistence, job orchestration, AI gateway | 8080 |
| `analysis-core` | Plain Java library (no Spring): language adapters, symbols, findings, fingerprint | – |
| `analysis-worker` | Spring Boot Kafka consumer that analyses corpus commits with `analysis-core` | 8081 |
| `miner` | Spring Boot JGit crawler that produces commit events | 8082 |
| `frontend` | Angular app (standalone components, lazy routes) | 4200 |
| `spark-jobs` | PySpark streaming + batch jobs (bronze/silver/gold) | – |
| `ai-lab` | Dataset splits, evaluation harness, fine-tuning notebooks, prompts | – |
| `infrastructure` | `docker-compose.yml` (Postgres + pgvector; Kafka under the `pipeline` profile, Spark under `spark`) | 5432 / 9092 / 7077 |
| `docs` | Specification, phase guide, ADRs | – |

Everything above is one Git repository, [aminekabli55/bcosmos](https://github.com/aminekabli55/bcosmos) on GitHub:
the nine former per-module repositories were imported under their folders with their history intact
([ADR 0014](docs/decisions/ADR-0014-one-git-repository.md), implemented 2026-11-04). There is still **no root
`pom.xml`** and no Maven reactor; builds run per module.

## Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| JDK | 21 (Temurin `jdk-21.0.12.101-hotspot`) | `java` is **not** on the PATH of a plain shell here, so the commands below set `JAVA_HOME` first |
| Maven | 3.10.0 | Use the wrapper (`./mvnw` in `backend`, `miner`, `analysis-worker`, `analysis-core`); no global install needed |
| Node.js | 24.19.0 (npm 11.17.0) | Required by the Angular 22 CLI in `frontend` |
| Python | 3.12.10 | Venvs already exist at `ai-lab/.venv` and `spark-jobs/.venv` |
| Docker | Docker Desktop (engine 29.x) | Postgres + pgvector for development, Kafka from Phase 4, Testcontainers in tests |

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Start the infrastructure

Postgres + pgvector is the only service needed for Phases 0–3. Kafka is added with the `pipeline` profile and
Spark with the `spark` profile; both are off by default.

```bash
cd infrastructure

# Postgres + pgvector only (port 5432)
docker compose up -d

# check it is healthy
docker compose ps

# later, from Phase 4: also start Kafka (port 9092)
docker compose --profile pipeline up -d

# later, from Phase 5: start the Spark standalone cluster (master UI 7080, master RPC 7077, worker UI 7081).
# Spark runs in Docker, not as a local PySpark install (ADR 0017). The UIs are published on 7080/7081 so they
# cannot collide with the backend (8080) and the analysis-worker (8081).
docker compose --profile spark up -d spark-master spark-worker

# run a job against the cluster: the driver runs in this one-off container (client mode),
# spark-jobs/ is mounted at /opt/jobs
docker compose --profile spark run --rm spark-submit \
  --master spark://spark-master:7077 /opt/jobs/batch/<job>.py

# prove the pgvector extension is enabled
docker compose exec postgres psql -U bcosmos -d bcosmos -c "SELECT extversion FROM pg_extension WHERE extname = 'vector';"

# stop the containers (data volume pgdata is kept)
docker compose down

# stop and delete the database volume as well
docker compose down -v
```

The backend creates the schema itself on startup (Liquibase), including `CREATE EXTENSION IF NOT EXISTS vector`, so
the database does not need any manual preparation.

**Port collision with a native PostgreSQL.** If the backend fails with
`FATAL: password authentication failed for user "bcosmos"` while `docker compose ps` reports the container as
healthy, a PostgreSQL server *installed on the host* (for example `postgresql-x64-18`) is also listening on
`0.0.0.0:5432`. Both binds succeed, but Windows answers `localhost:5432` with the host server, which does not know
this project's credentials. The compose file publishes `5433` for exactly that case:

```bash
docker compose up -d
BCOSMOS_DB_PORT=5433 ./mvnw spring-boot:run     # 5433 on the host is only this container
```

Nothing else has to change: `BCOSMOS_DB_PORT` is the documented override, and Testcontainers (used by
`./mvnw verify`) always picks its own random port, so the suites are unaffected.

The **dev** profile's connection defaults (matching the compose file) are
`jdbc:postgresql://localhost:5432/bcosmos`, user `bcosmos`, password `bcosmos`. All of it is overridable:

| Variable | Default | Used by |
|---|---|---|
| `BCOSMOS_DB_HOST` | `localhost` | backend, analysis-worker |
| `BCOSMOS_DB_PORT` | `5432` | backend, analysis-worker |
| `BCOSMOS_DB_NAME` | `bcosmos` | backend, analysis-worker |
| `BCOSMOS_DB_USER` | `bcosmos` | backend, analysis-worker |
| `BCOSMOS_DB_PASSWORD` | `bcosmos` | backend, analysis-worker |
| `SERVER_PORT` | 8080 / 8081 / 8082 | backend, analysis-worker, miner |

Never commit real credentials: `.env` is ignored in every module. A provider key comes either from an environment
variable or from a key pasted into the Settings screen that the backend stores encrypted — never from a file, and
never back out of an API response (ADR 0007, ADR 0019, ADR 0038).

## AI gateway (Phase 2)

Analysis itself never needs an API key. Everything AI is opt-in twice over: the global switch has to be on **and**
the registry row has to be enabled, and enabling a row whose provider has no key is refused with the variable name
to set.

```bash
# optional: turn the AI layer on for the dev profile (it already is) and give its provider a key
BCOSMOS_AI_ENABLED=true GEMINI_API_KEY=... ./mvnw spring-boot:run

# the provider registry: endpoint, protocol, and where each credential comes from (never the key itself)
curl localhost:8080/api/providers

# add a vendor — a code, the protocol it speaks, its base URL and optionally a pasted key that is encrypted
# before it is stored; the key is never returned, only a four-character hint
curl -X POST localhost:8080/api/providers -H 'Content-Type: application/json' \
  -d '{"code":"deepseek","label":"DeepSeek","protocol":"OPENAI_COMPATIBLE","baseUrl":"https://api.deepseek.com/v1","apiKeyEnv":"DEEPSEEK_API_KEY","apiKey":"sk-..."}'

# list the registry: provider, model, enabled, default, and whether a credential is present (never the key itself)
curl localhost:8080/api/models

# enable a model and make it the default
curl -X PUT localhost:8080/api/models/1 -H 'Content-Type: application/json' \
  -d '{"enabled": true, "defaultModel": true}'

# the AI master switch: where it stands, and turning it off for this deployment
curl localhost:8080/api/settings/ai
curl -X PUT localhost:8080/api/settings/ai -H 'Content-Type: application/json' -d '{"enabled": false}'
# {"enabled": null} clears the override and follows bcosmos.ai.enabled again

# explain one stored finding; omit the body to use the default model
curl -X POST localhost:8080/api/findings/9/explanations \
  -H 'Content-Type: application/json' -d '{"modelId": 1}'
```

The response carries the answer, the evidence it cites, the model and prompt version that produced it, the tokens,
the latency, the estimated cost, and `rejectedAttempts` — the schema problems of a first answer that had to be
retried. Usage is stored per call in `llm_usage`, including failed and timed-out calls.

| Variable | Default | Meaning |
|---|---|---|
| `BCOSMOS_AI_ENABLED` | `false` (global) / `true` (dev profile) | master switch; off means nothing is sent to any provider |
| `BCOSMOS_AI_SECRET_KEY` | – | base64 of 32 random bytes (`openssl rand -base64 32`): the key that encrypts credentials pasted in Settings; absent means pasting one is refused and providers read their environment variables instead. In the `dev` profile a key is generated into `~/.bcosmos/ai-secret.key` on first start, so pasting one works out of the box ([ADR 0039](docs/decisions/ADR-0039-development-master-key.md)); this variable always overrides it |
| `GEMINI_API_KEY` / `DEEPSEEK_API_KEY` / … | – | read when a provider row has no stored key; which variable each row reads is data (`api_key_env`), not configuration |
| `BCOSMOS_AI_TIMEOUT`, `BCOSMOS_AI_MAX_ATTEMPTS`, `BCOSMOS_AI_INITIAL_BACKOFF` | `30s`, `3`, `500ms` | budget and retry policy of one call |

A rate limit is its own failure, not a generic provider error: HTTP 429 is reported as `rate-limited`, and the retry
waits what the provider itself asked for — the `Retry-After` header, or Google's `RetryInfo.retryDelay` in the error
body — as long as that fits inside `BCOSMOS_AI_TIMEOUT`. A quota whose wait does not fit fails at once with the wait
in the message rather than as a timeout that says nothing, because a per-minute allowance is survived by waiting for
it and a per-day one cannot be retried into success at all
([ADR 0040](docs/decisions/ADR-0040-rate-limit-backoff.md)).

## RAG and suggestions (Phase 3)

The first call for an analysis run also indexes its code: one chunk per method or class, embedded with an enabled
`EMBEDDING` registry row and stored in `code_chunk`. The index is reused for later questions about the same run, so
only the first call pays for it. `GET /api/models` now reports each row's `capability` (`CHAT` or `EMBEDDING`) and an
embedding row's `embeddingDimension`; a chat model cannot embed and an embedding model cannot chat.

An index build is a sequence of embedding calls rather than one answer, so it sits out its own rate limits: when the
provider refuses a batch and says how long to wait, the build sleeps that long and retries the batch, up to
`BCOSMOS_RETRIEVAL_RATE_LIMIT_PATIENCE` (`bcosmos.retrieval.rate-limit-patience`, default `2m`) per build. One call
still fails fast, and a quota asking for longer than the patience fails the build with the wait in the message
([ADR 0041](docs/decisions/ADR-0041-rate-limit-patience-for-a-sequence.md)).

```bash
# ask for a validated fix suggestion; omit the body to use the registry defaults
curl -X POST localhost:8080/api/findings/9/suggestions \
  -H 'Content-Type: application/json' -d '{"modelId": 1, "embeddingModelId": 2}'

# the suggestions already stored for a finding (paginated), and one by id
curl localhost:8080/api/findings/9/suggestions
curl localhost:8080/api/suggestions/4

# accept, reject or defer; this also moves the finding's triage status
curl -X POST localhost:8080/api/suggestions/4/review \
  -H 'Content-Type: application/json' -d '{"status": "ACCEPTED"}'

# one finding by id, which is how the review screen opens without a loaded list
curl localhost:8080/api/findings/9
```

The response carries the answer (summary, explanation, citations, `proposedDiff`, risks, tests to run,
uncertainties), the verdict (`schemaOk`, `evidenceOk`, `diffApplies`, `parses`, `targetFindingGone`,
`newFindings[]`, `allChecksPassed`, `notes`) and the retrieval that produced it (every evidence item with its file,
line range, kind and similarity, plus the token estimate, whether the bundle was truncated, and the index state).

**A suggestion is a proposal.** No code path writes a diff to a repository — accept/reject/defer changes the
finding's triage state and nothing else. The verdict is also not an approval: `targetFindingGone` means the
baseline rule no longer reports on the patched lines, and `notes` records what was *not* checked, notably that the
patched file was parsed and analysed, not compiled.

## Configuration

Every Spring app separates what is the same everywhere from what is not, and nothing environment-specific sits in a
global file (ADR 0018, amended in Phase 10 — `analysis-worker` and `miner` use the same shape as `backend`):

| File | Applies to | Holds |
|---|---|---|
| [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) | every environment | application name, port, JPA `validate`, Liquibase changelog, actuator exposure, repository limits |
| `application-dev.yml` | local development — **the profile you get when nothing is set** | localhost datasource, `BCOSMOS_ALLOWED_ROOTS` defaulting to your home directory, health details |
| `application-prod.yml` | `SPRING_PROFILES_ACTIVE=prod` | credentials and allowed roots from the environment only; it refuses to start without them |
| [backend/src/test/resources/application-test.yml](backend/src/test/resources/application-test.yml) | tests (`@ActiveProfiles("test")`) | tiny pool, fixture allowed roots, quiet logs; the database comes from Testcontainers |

`analysis-worker` and `miner` carry the same three files: `application.yml` (identical everywhere, e.g. the Kafka
consumer group and deserializers), `application-dev.yml` (localhost broker and database, clone directory, health
details — the profile you get when nothing is set) and `application-prod.yml` (`SPRING_PROFILES_ACTIVE=prod`, where
the broker, the credentials and the clone directory have **no** defaults, so a misconfigured production start stops
instead of falling back to a developer machine).

Configuration is YAML only. A `.properties` file silently wins over the YAML file of the same base name, which is
exactly the bug found in `miner` (a duplicate `application.properties` shadowing `application.yml`) and the reason it
was deleted; the `.properties` files that remain are data — the PMD and ESLint rule baselines and the rule
descriptions.

So `./mvnw spring-boot:run` and running the jar both use the **dev** profile, while production is always explicit.
The test profile lives on the test classpath and can never ship inside the jar; it deliberately declares no
datasource URL, so a test that forgets Testcontainers fails on startup instead of writing to your local database.

## Build and test

`analysis-core` is consumed from the local Maven repository, so install it once before building the modules that
depend on it (ADR 0016):

```bash
# one-time (and after every change in analysis-core)
cd analysis-core && ./mvnw install

cd ../backend          && ./mvnw verify   # unit + integration tests (Testcontainers: Postgres + Kafka)
cd ../analysis-worker  && ./mvnw verify
cd ../miner            && ./mvnw verify

cd ../frontend         && npm ci && npm run build
cd ../frontend         && npm test -- --watch=false   # vitest through the Angular unit-test builder
cd ../spark-jobs       && ./.venv/Scripts/python.exe -m pytest -q
cd ../ai-lab           && ./.venv/Scripts/python.exe -m pytest -q
```

The `spark-jobs` venv is for fast offline unit tests only; jobs are executed through the `spark` compose profile
(ADR 0017), so no local PySpark/Hadoop setup is needed to run them.

`backend`'s integration tests start a throwaway `pgvector/pgvector:pg17` container, apply the real Liquibase
changelog and assert `GET /actuator/health` returns `UP`, so Docker must be running for `mvn verify`.

## Verify the whole project, step by step

Every suite in dependency order, with the result each one produces today. Docker Desktop has to be running for the
JVM suites; the Spark suite runs in the `spark-test` container because Spark cannot start on the Windows host.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH:/c/Users/amine/AppData/Local/Programs/DockerDesktop/resources/bin"
```

| # | Command | Expected result |
|---|---|---|
| 1 | `cd analysis-core && ./mvnw test` | 77 tests, 0 failures |
| 2 | `cd analysis-core && ./mvnw install -DskipTests` | the jar the other modules resolve from `~/.m2` |
| 3 | `cd backend && ./mvnw verify` | 182 tests, 0 failures, 1 skipped; Testcontainers applies changesets 0001–0023 |
| 4 | `cd analysis-worker && ./mvnw verify` | BUILD SUCCESS (consumers, duplicate delivery, event wire contract) |
| 5 | `cd miner && ./mvnw verify` | 4 tests, 0 failures |
| 6 | `cd frontend && npm test -- --watch=false` | 97 tests in 14 files |
| 7 | `cd frontend && npm run build` | exit 0, no budget warnings |
| 8 | `cd ai-lab && ./.venv/Scripts/python.exe -m pytest -q` | 28 tests |
| 9 | `cd infrastructure && docker compose --profile spark run --rm spark-test -q tests` | 51 tests |

## Run the modules

```bash
cd backend && ./mvnw spring-boot:run     # http://localhost:8080/actuator/health
cd analysis-worker && ./mvnw spring-boot:run   # http://localhost:8081/actuator/health
cd miner && ./mvnw spring-boot:run       # http://localhost:8082/actuator/health
cd frontend && npm start                 # http://localhost:4200
```

On Linux/macOS activate the Python venvs with `source .venv/bin/activate` instead of the Windows path above.

If the `PORT` environment variable is set, the Angular dev server binds to that value instead of 4200; unset it (and
pass `--port 4200`) when you want the documented URL.

Three startup failures that look like code bugs and are not:

- **`Factory method 'typeScriptAdapter' threw exception ... org/treesitter/TSLanguage`** — the classpath that was
  used does not carry the tree-sitter binding. It *is* there for a Maven launch:
  `./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt` in `backend` lists
  `.../io/github/bonede/tree-sitter/0.25.3/tree-sitter-0.25.3.jar`, the packaged jar contains it under
  `BOOT-INF/lib/`, and both `backend` and `analysis-worker` now declare it directly (ADR 0036) because they are the
  modules that build the adapter. Seeing this error therefore means the launcher is using a cached classpath: reload
  the Maven projects in the IDE and rebuild. `./mvnw spring-boot:run` remains the control run.
- **`Web server failed to start. Port 8080 was already in use`** — something else owns the backend's port. Check with
  `netstat -ano | grep :8080` (the last column is the PID) and `docker compose ps` in `infrastructure/`. The `spark`
  profile *used* to publish its master UI on 8080 and its worker UI on 8081; the compose file now uses 7080/7081
  (ADR 0017), so a container still holding 8080 is an old one — recreate it:
  `docker compose --profile spark up -d --force-recreate spark-master spark-worker`. If it is your own second
  backend, remember the Angular dev server proxies `/api` to 8080, so stop the other one instead of moving this one.
- **`FATAL: password authentication failed for user "bcosmos"`** — a native PostgreSQL on the host holds
  `0.0.0.0:5432` and answers before Docker's proxy does. The dev profile already defaults to **5433**, which compose
  publishes on every machine, so this only appears if `BCOSMOS_DB_PORT` was set to 5432 by hand.

## Phase 1 flow in the browser

The frontend reaches the backend through the dev-server proxy: [frontend/proxy.conf.json](frontend/proxy.conf.json)
forwards `/api` to `http://localhost:8080`, so the backend has to be running on that port.

1. `docker compose up -d` in `infrastructure/` (Postgres + pgvector).
2. `cd backend && ./mvnw spring-boot:run` — the dev profile creates the schema itself.
3. `cd frontend && npm start`, then open <http://localhost:4200>.
4. Register a repository path that sits under an allowed root (the dev default is your home directory), press
   **Analyse**, and open **Findings** on the run once it finishes.

[sample-data/java-fixture](sample-data/java-fixture/README.md) is the fixture this flow is checked against; its
README lists the exact findings a run must produce. Ready-to-paste paths for both fixtures, and a screen-by-screen
tour of all ten screens, are in [docs/using-bcosmos.md](docs/using-bcosmos.md).

## Phase 8 flow in the browser (Code Cosmos & History)

1. Open **Cosmos** (<http://localhost:4200/cosmos>):
   - Select your registered repository and latest analysis run from the top dropdowns.
   - Explore the 2D Cytoscape dependency graph: zoom, pan, and switch layouts (Force-Directed CoSE, Concentric by hotspot score, Hierarchical).
   - Use the search bar to filter nodes by name or package.
   - Click any symbol node to open the side drawer: inspect symbol signature, file lines, findings count, and click **Calculate Blast Radius** to see transitive callers and dependents highlighted directly on the canvas.
   - Browse the **Architectural Hotspots** list ranked by the formula `score = (findings * 10) + (highSeverity * 20) + (inDegree * 5) + (lineCount * 0.05)`; click any hotspot to center and zoom into it.
   - Switch to **Accessible Table** for full keyboard-navigable, accessible tabular navigation.
2. Open **History** (<http://localhost:4200/history>):
   - Pick a baseline snapshot run and a target snapshot run, then click **Compare Snapshots**.
   - Review the transition stat cards: `NEW` (regressions), `RESOLVED` (verified fixes), `PERSISTENT` (still open), and `UNCERTAIN` (parse failures or disabled rule safeguards).
   - Filter transitions by category tab or search by rule name or file path.
   - Review each finding's deterministic fingerprint, confidence percentage, and human-readable transition reason.

### Frontend styling

Hand-rolled, dependency-free CSS: [frontend/src/styles.scss](frontend/src/styles.scss) holds the design tokens
(colour, spacing, radius, shadow, the monospace stack) and the shared element styles — cards, tables, badges,
buttons, inputs, state and metric blocks. Component styles stay local to their component
([app.scss](frontend/src/app/app.scss), `repositories/`, `quality/`, `settings/`, `ai-review/`) and only add layout.
Severity is carried by a badge dot plus its text label as well as colour, as the spec requires. PrimeNG is still not
installed.

## Database migrations

Liquibase owns the schema and only `backend` runs it (ADR 0009):

- master changelog: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- changesets: `backend/src/main/resources/db/changelog/changes/NNNN-*.yaml`
- `analysis-worker` and `miner` never migrate; JPA runs with `ddl-auto: validate` everywhere.

## Known limitations

- Triage moves only through a suggestion review (`POST /api/suggestions/{id}/review`); there is no endpoint to
  accept or reject a finding without asking a model first.
- The UI calls a relative `/api` path, so the built bundle expects the same origin or a proxy; an absolute API base
  URL is not configurable yet.
- The AI switch is stored, but the deployment property is only its *default*: a deployment that needs a switch the
  browser can never turn back on has no mechanism yet (a kill-switch above the stored value is a follow-up,
  [ADR 0020](docs/decisions/ADR-0020-stored-ai-switch.md)).
- Ignore paths, the other half of spec section 9's Settings screen, are still a backend property
  (`bcosmos.repository.ignored-directories`), not a stored setting.
- The JSON Schema validation in the gateway implements `type`, `required`, `properties`, `items` and `enum`, and
  ignores other keywords on purpose ([ADR 0019](docs/decisions/ADR-0019-ai-gateway.md)); a full JSON Schema library
  is a separate decision.
- The `spark-jobs` unit suite must run in the `spark-test` container: on Windows every Spark test fails in the
  local `spark-jobs/.venv` with `py4j.protocol.Py4JJavaError`, because Spark cannot start without winutils
  ([ADR 0017](docs/decisions/ADR-0017-spark-in-docker.md)). `ai-lab` runs locally.
- No root multi-module build and no CI yet (ADR 0016).
- Deleting a repository is irreversible: it takes its runs, findings and commit reviews with it, and there is no
  archive or undo ([ADR 0037](docs/decisions/ADR-0037-repository-deletion.md)). It also does not cancel work already
  on Kafka, and the `agg_*` gold tables keep the repository until the next Spark load.
- `docs/BCOSMOS_SPEC.md` is an older duplicate of `docs/BCOSMOS_SPEC_v2.md`; keep one.
- Docker Compose support in Spring Boot is deliberately not added: the compose file lives in `infrastructure/`, so
  it is started manually with the commands above.
- PrimeNG is not installed yet (the spec's Phase 0 frontend list marks it optional until a screen needs it);
  `cytoscape`, `chart.js` and `@types/cytoscape` are.
