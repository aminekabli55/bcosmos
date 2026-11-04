# BCosmos — Specification v2

**Status:** Proposed baseline v2.1 (replaces v1.1 and v2)
**Changes in v2.1:** hosted LLM APIs only (no local models), new Commit Review feature, language-adapter design with TypeScript as the second language, honest positioning vs SonarQube, smaller starting corpus (about 20 repos).
**Goals:**
1. Learn applied AI engineering hands-on (LLM gateway, RAG, evaluation, fine-tuning), not just call an API.
2. Showcase Kafka and Spark on a workload that genuinely needs them.
3. Keep a clean architecture and a demo that works end to end.
4. Be useful to new developers, existing developers and scrum masters (code map, commit review, risk warnings).

---

## Current status (update after each step)

- **Phase 0 (foundation) complete — 2026-10-12.** **Phase 1 (core slice) complete — 2026-10-13.**
- **Phase 2 (AI gateway) complete — 2026-10-14.** One provider-neutral `LlmClient` with a Gemini and an
  OpenAI-compatible adapter, the `llm_model` registry (seeded, disabled by default) and `GET/PUT /api/models`,
  `llm_usage` recording for every call including failures, secret redaction, a JSON-Schema-validated `structured`
  path with a single retry that reports both attempts, versioned prompt files, retries with backoff under a total
  time budget, and `POST /api/findings/{id}/explanations`. The Settings screen (2026-10-14) drives the registry and
  the AI master switch from the browser; the switch is stored in `app_setting` and overrides the
  `bcosmos.ai.enabled` deployment default (`GET/PUT /api/settings/ai`). `cd backend && ./mvnw verify` is green (91
  tests, 0 failures, 1 skipped) and `cd frontend && npm test -- --watch=false` is green (35 tests); the manual
  check explained one real finding with two models against fake providers and drove both switches through the real
  API from the browser. See [PHASE_AUDIT.md](PHASE_AUDIT.md), [ai-design.md](ai-design.md),
  [ADR 0019](decisions/ADR-0019-ai-gateway.md) and [ADR 0020](decisions/ADR-0020-stored-ai-switch.md).
- Phase 1 status: registration + `SafePathValidator`, file discovery with ignore rules, the `repository` /
  `analysis_run` / `job` / `file_record` / `symbol` / `finding` tables, the in-process `JobDispatcher`, the REST
  API and the Repositories and Quality screens are implemented. `cd backend && ./mvnw verify` is green (23 tests,
  0 failures, 1 skipped) and boots the real application context; `cd frontend && npm test -- --watch=false` is
  green (20 tests). The browser manual check was run against `sample-data/java-fixture`: register, analyse, and
  compare the findings with the fixture's known problems — see [PHASE_AUDIT.md](PHASE_AUDIT.md).
- **Spark decision (2026-10-12):** Spark runs in Docker as a standalone cluster under the compose `spark` profile,
  not as a local PySpark install ([ADR 0017](decisions/ADR-0017-spark-in-docker.md)); verified by submitting a
  PySpark job against `apache/spark:4.2.0-python3` and getting 2 executors and a correct hand-checked aggregate.
- Done: Bitbucket repositories created and push problem fixed; `backend`, `frontend`, `analysis-worker`, `miner`, `analysis-core`, `spark-jobs` (PySpark 4.2.0) and `ai-lab` initialized; `infrastructure/docker-compose.yml` (Postgres + pgvector, Kafka under profile `pipeline`); Liquibase baseline with the first changeset `CREATE EXTENSION IF NOT EXISTS vector`; backend datasource from `application.yml` with environment variable overrides; `GET /actuator/health` returns `UP`; `README.md` and `AGENTS.md`; ADRs 0001–0016 in `docs/decisions/`.
- Verified with real command output: `./mvnw verify` green in `backend` (3 tests: context loads, Liquibase created the `vector` extension, health endpoint `UP`), in `analysis-worker`, in `miner` (1 test each) and in `analysis-core`; `ng build` exit 0; `pytest` runs in `spark-jobs` and `ai-lab` (no tests collected, allowed).
- **Layout decision (2026-10-12):** keep one Git repository per module for now and **do not create the root Maven multi-module build** (ADR 0016). `analysis-core` is resolved by installing it into the local Maven repository (`./mvnw install` in `analysis-core` before building `backend`/`analysis-worker`). ADR 0014 stays as the intended end state; the monorepo migration is a separate task.
- Test images are pinned to `pgvector/pgvector:pg17` and `apache/kafka-native:4.3.1`, matching the compose file, so the first changeset runs in tests too.
- **UI pass (2026-10-14):** the frontend moved onto a shared design system in `frontend/src/styles.scss` (tokens,
  cards, tables, buttons, badges with a severity dot, state and metric blocks), with a branded shell, a redesigned
  Repositories screen and a restyled Quality screen with a sticky evidence panel; no component library was added.
- **Phase 3 (RAG and suggestions) complete — 2026-10-14.** `SymbolChunker` writes one `code_chunk` per method or
  class with its embedding (pgvector + HNSW), `Retriever` builds a token-budgeted evidence bundle (enclosing
  declaration, callers/callees, similar chunks, the baseline rule's description), `SuggestionService` asks a chat
  model for a fix, and `PatchValidator` computes the verdict BCosmos stands behind: `schemaOk`, `evidenceOk`,
  `diffApplies` (the patch is applied in memory to the analysed revision), `parses`, `targetFindingGone` (the
  baseline rule set is re-run) and `newFindings[]`, plus `notes` for what was *not* checked. Endpoints
  `POST /api/findings/{id}/suggestions`, `POST /api/suggestions/{id}/review`, and the additions
  `GET /api/findings/{id}/suggestions`, `GET /api/suggestions/{id}`, `GET /api/findings/{id}`. The `suggestion`
  table keeps the answer and its verdict, so reopening a suggestion costs no model call, and the `code_chunk` index
  is reused per run. The AI Review screen shows the evidence, the explanation, the diff, the verdict check by check
  and the accept/reject/defer decision; a patch is never applied to a repository. `cd backend && ./mvnw verify` is
  green (125 tests, 0 failures, 1 skipped), `cd analysis-core && ./mvnw test` is green, and
  `cd frontend && npm test -- --watch=false` is green (43 tests). The rule baseline changed in this phase:
  `UnusedImports` (removed from PMD 7) and `UnusedLocalVariable` (silently not reported on the simplest case under
  PMD 7.28.0) were dropped and `UnusedPrivateMethod` + `AvoidCatchingGenericException` added; a reference that
  resolves to no rule now fails loudly, and `JavaAdapterAnalyzeTest.everyBaselineRuleFires` pins that each listed
  rule really fires. See [PHASE_AUDIT.md](PHASE_AUDIT.md), [ai-design.md](ai-design.md),
  [ADR 0021](decisions/ADR-0021-rag-and-validated-suggestions.md) and [rule-catalog.md](rule-catalog.md).
- **Phases 0 through 10 complete — 2026-10-15.**
  - **Phase 1 (core slice):** Local Java registration, safe paths, Liquibase tables, in-process job dispatcher, Repositories and Quality screens.
  - **Phase 2 (AI gateway):** Provider-neutral `LlmClient` (Gemini, OpenAI), `llm_model` registry, `llm_usage`, secret redaction, structured output, prompt versioning, Settings screen.
  - **Phase 3 (RAG & suggestions):** `SymbolChunker`, pgvector HNSW embeddings, `Retriever`, `SuggestionService`, `PatchValidator` verdicts, AI Review screen.
  - **Phase 4 (Kafka pipeline & corpus ingestion):** `CommitAnalysisPipeline`, `FileSource` port, worker consumers with idempotent sinks (`worker_file_result`), miner HTTPS allowlist, DLQ backoff, Corpus screen.
  - **Phase 5 (Spark lakehouse):** Medallion layout (bronze/silver/gold), fix-triple dataset & card, Analytics screen with Chart.js drill-downs.
  - **Phase 6 (Evaluation harness):** `benchmark_run` table, Python evaluation harness, side-by-side model comparison, Benchmarks leaderboard screen.
  - **Phase 7 (Fine-tuning):** QLoRA dataset preparation CLI, Colab/Kaggle notebook, evaluation runner, adapter merge, fine-tuned model registration.
  - **Phase 8 (Code Cosmos & history):** Java relationships (`CALLS`, `EXTENDS`, `IMPLEMENTS`, `USES_TYPE`), Cytoscape 2D Cosmos graph, blast-radius impact analysis, snapshot history comparison.
  - **Phase 9 (Commit Review):** Three-layer evidence review (Facts, Risk signals, optional AI summary with citation verification), Commit Review screen.
  - **Phase 10 (TypeScript adapter):** `TypeScriptAdapter` (Tree-sitter 0.25.3 + ESLint baseline `no-empty`, `no-unused-vars`, `no-explicit-any`, `no-console`), shared contract suite `LanguageAdapterContractTest`, multi-adapter pipeline/worker, `sample-data/angular-fixture`, UI support levels (`Java · L3`, `TypeScript · L2`) ([ADR 0033](decisions/ADR-0033-typescript-adapter.md)). All 10 phases of the BCosmos roadmap are complete.
- **Phase 10 follow-ups, found by driving the running application — 2026-10-15.** A registered TypeScript
  repository displayed `Java · L3` because `repository.primary_language` was still the Phase 1 constant; it is now
  the language of the files a finished run really analysed ([ADR 0034](decisions/ADR-0034-repository-primary-language.md)).
  A TypeScript run recorded `javaparser`/`pmd-java` as its tools because run metadata was read from the first
  registered adapter before discovery; it now comes from the adapter that handled most of the discovered files
  ([ADR 0035](decisions/ADR-0035-run-tool-metadata-from-dominant-adapter.md)). The parser binding a module
  instantiates is now its own direct dependency rather than an implicit transitive one, because a cached classpath
  made the whole application fail to start with `org/treesitter/TSLanguage`
  ([ADR 0036](decisions/ADR-0036-parser-binding-as-direct-dependency.md)), and the common+dev/prod YAML split of
  [ADR 0018](decisions/ADR-0018-configuration-profiles.md) now covers `analysis-worker` and `miner` too.
  `cd backend && ./mvnw verify` is green (182 tests, 0 failures, 1 skipped).

- **Phase 11 (providers and models as data) complete — 2026-10-16.** A provider is a database row instead of a
  compile-time enum: `llm_provider` (changeset `0025`) carries `code`, `protocol` (which selects the adapter),
  `base_url`, and a credential that is either an environment variable name or a key pasted into the Settings screen
  and stored AES-256-GCM encrypted under `BCOSMOS_AI_SECRET_KEY` (plaintext never a column, never a response —
  `credentialSource` + a four-character hint instead). `llm_model.provider_id` replaces the `provider` column
  (backfilled, unique on `(provider_id, model_name)`), `GET/POST/PUT/DELETE /api/providers` lets an operator add
  DeepSeek, Claude or a local server from the browser without a redeployment (seeds: `gemini`, `openai`,
  `local-openai`, `deepseek`, `claude`), and the same screen registers any model id through `POST/DELETE
  /api/models`. Validation: `cd backend && ./mvnw test` unit suite green (**168 tests**, 0
  failures, 1 skipped — this host has no Docker, so the Testcontainers suite behind `./mvnw verify` could not run
  here; `LiquibaseChangelogParseCheck` generates the full changelog to SQL including 0025),
  `cd frontend && npm test -- --watch=false` green (**117 tests**, 14 files), `npm run build` exit 0
  ([ADR 0038](decisions/ADR-0038-provider-registry-in-the-database.md)).

Module types: `backend`, `analysis-worker`, `miner` are Spring Boot. `analysis-core` is a plain Java library (no Spring). `spark-jobs` and `ai-lab` are Python.

---

## 0. Rules for the AI coding agent

1. Read this file fully, inspect the repo, state the phase and the files you intend to change.
2. Implement **one phase / vertical slice at a time**. Never the whole roadmap.
3. Add tests for every behavior change. Report real commands and real results; never claim tests passed if they were not run.
4. Treat analyzed repositories as **untrusted input**: never run their scripts, hooks or build tools.
5. No placeholder implementations presented as complete.
6. Pin dependency versions at implementation time; do not copy stale versions.
7. Keep `README.md` and `docs/` in sync. Record material decisions as ADRs in `docs/decisions/`.
8. Report after each task: **Implemented / Files changed / Data & architecture changes / Validation / Known limitations / Next step**.
9. If code and this document disagree, flag it and propose a resolution before continuing.

---

## 1. Product

### 1.1 One-liner
BCosmos turns repositories (Java first) into an evidence-backed map of architecture and quality, reviews every new commit, mines open-source history at scale, and uses LLMs, evaluated and fine-tuned on that data, to explain and fix findings.

### 1.1b Positioning (honest)
Tools like SonarQube (rules) and CodeRabbit / Copilot review (AI) already cover part of this. BCosmos is **not** a "Sonar killer". It is a **lab for AI code review on top of static analysis**: a rule tool (PMD) finds problems, then AI explains and suggests fixes, and BCosmos **measures** how good those fixes are. The value for you is what you build and learn (gateway, RAG, evaluation, pipeline, fine-tuning), not market competition.

### 1.2 What makes it distinctive
- Every AI suggestion is **validated automatically**: the patch must apply, the target finding must disappear, and no new findings may appear in the file. This gives objective model metrics instead of vibes.
- The Kafka + Spark pipeline mines real commit history and produces **(code before, finding, code after) triples**, which become the evaluation set and the fine-tuning dataset.
- Several hosted models (and later a fine-tuned one) can be **compared side by side** on the same finding and on a benchmark.
- Every new commit gets an **evidence-based review** (facts first, AI summary optional), useful for reviewers and scrum masters.
- The design is **language-agnostic** through language adapters. Java is first, TypeScript is the proof of a second language.

### 1.3 Non-goals
- Training a model from scratch.
- Deep support for many languages from day one. **Java is the first language; TypeScript is added late to prove the design** (see section 3.1).
- Running local LLMs. All models are used through hosted APIs.
- Multi-tenant SaaS, billing, Kubernetes, a graph database.
- Automatic application of patches to a user's repository (suggestions are always review-only).
- Claims that the hotspot score predicts bugs. It is a triage heuristic.

### 1.4 Use cases

| ID | Use case | Actor | Outcome |
|---|---|---|---|
| UC1 | Register a local Java repository | Developer | Validated, canonical path stored; nothing executed |
| UC2 | Run an analysis and follow its status | Developer | `AnalysisRun` with stage, counts, errors, parse coverage |
| UC3 | Browse and filter findings | Developer | Table with rule, severity, file/line evidence, source preview |
| UC4 | Explore dependencies and estimated impact | Developer / reviewer | Bounded 2D graph, callers/dependents, unresolved edges flagged |
| UC5 | Get an AI explanation and proposed fix | Developer | Cited evidence, diff, validation verdict, risks |
| UC6 | Choose a model, or compare models on one finding | Developer | Side-by-side diffs with latency, tokens, validation verdicts |
| UC7 | Compare two snapshots | Tech lead | NEW / RESOLVED / PERSISTENT findings and metric deltas |
| UC8 | Ingest a corpus of open-source repositories | Operator | Commits flow through Kafka into analysis, Parquet and Postgres |
| UC9 | View corpus-wide analytics | Tech lead | Rule distributions, time-to-fix, hotspots, trends (from Spark) |
| UC10 | Build the fix dataset | ML engineer (you) | Versioned train/validation/test splits of fix triples |
| UC11 | Run the model benchmark | ML engineer (you) | Leaderboard: validity rates, latency, cost per model |
| UC12 | Fine-tune a small model and add it to the registry | ML engineer (you) | Model is on the leaderboard (and in the selector if it is hosted) |
| UC13 | Review a new commit or commit range | Reviewer / scrum master | Facts, risk signals, and an optional AI summary with evidence |
| UC14 | Understand a project (onboarding) | New developer | Code map plus optional AI summaries of packages and classes, with citations |
| UC15 | Add support for a new language | Developer | New adapter passes the shared contract tests |

---

## 2. Architecture

### 2.1 Components

```
                 ┌──────────────────────────┐
                 │  Angular frontend        │
                 └────────────┬─────────────┘
                              │ REST
                 ┌────────────▼─────────────┐        ┌────────────────┐
                 │  backend (Spring Boot)   │───────▶│ Hosted LLM APIs│
                 │  modular monolith        │        │ (many vendors) │
                 └───┬──────────────┬───────┘        └────────────────┘
                     │              │
          Postgres + pgvector       │ Kafka
                     ▲              ▼
        ┌────────────┴───┐   ┌──────────────┐    ┌───────────────┐
        │ analysis-worker│◀──│    Kafka     │◀───│ miner         │
        │ JavaParser+PMD │──▶│  (KRaft)     │    │ JGit crawler  │
        └────────────────┘   └──────┬───────┘    └───────────────┘
                                    │
                         ┌──────────▼───────────┐
                         │ Spark Structured     │──▶ Parquet bronze
                         │ Streaming            │
                         └──────────────────────┘
                                    │
                         ┌──────────▼───────────┐
                         │ Spark batch jobs     │──▶ Parquet silver/gold ──▶ Postgres agg_* tables
                         └──────────┬───────────┘
                                    │
                         ┌──────────▼───────────┐
                         │ ai-lab (Python)      │  dataset splits, eval harness, QLoRA fine-tuning
                         └──────────────────────┘
```

### 2.2 Architecture decisions (ADRs to write)

| ADR | Decision | Reason |
|---|---|---|
| 0001 | `backend` is a **modular monolith**; `analysis-worker` and `miner` are separate processes | The worker has a real independent scaling need; the backend does not |
| 0002 | PostgreSQL + **pgvector** is the canonical store | One database for relational data, graph edges and embeddings |
| 0003 | **Kafka** for commit/analysis events | Per-repo ordering, parallel consumers, replay, backpressure |
| 0004 | **Spark** for corpus analytics and dataset building | Workload is many repos × many commits × many findings |
| 0005 | **PySpark** for Spark jobs | Shares tooling with `ai-lab`; Java stays for product code (override if you prefer Java/Scala) |
| 0006 | **Python only in `ai-lab` and `spark-jobs`** | ML ecosystem lives there; product stays Java |
| 0007 | LLM access only through a provider-neutral `LlmClient` | Swap/compare models without touching business logic |
| 0008 | **Cytoscape.js (2D)** graph instead of Three.js | Same value, far less UX risk. Large graphs need clustering anyway |
| 0009 | **Liquibase** for migrations | Already familiar |
| 0010 | JSON events with an explicit envelope and `schemaVersion` | Simple; Avro + Schema Registry is a drop-in upgrade |
| 0011 | Analyze files **by commit blob (JGit), not by checkout** | Fast and safe: no working tree, nothing executed |
| 0012 | Language support through a **`LanguageAdapter`** interface | Everything outside the adapter stays language-independent |
| 0013 | All LLMs through **hosted APIs**, no local model required | Weak local hardware; easy model comparison; free tiers for testing |
| 0014 | **One Git repository (monorepo)** named `bcosmos`, inside one Bitbucket project `BCosmos` | One place for the spec, shared `analysis-core`, one CI pipeline, and the AI agent sees all the code |
| 0015 | **Java 21 (LTS)** for `backend`, `analysis-core`, `analysis-worker`, `miner` and the Spark JVM | Supported by Spring Boot, Kafka, Spark, PMD and JavaParser. Java 25 (newer LTS) is possible later, but only on 25.0.3 or newer and after checking each tool |

### 2.3 Data layers (medallion)

| Layer | Storage | Content |
|---|---|---|
| Serving | Postgres | repositories, runs, files, symbols, relationships, findings, suggestions, model registry, `agg_*` tables |
| Bronze | Parquet (local FS) | Raw `analysis.findings.v1` events as streamed from Kafka |
| Silver | Parquet | Cleaned and deduplicated findings, commits and metrics; schema-versioned |
| Gold | Parquet → Postgres `agg_*` | Aggregates the dashboards read; fix-triple dataset |

---

## 3. Modules and repository structure

```
bcosmos/
├── README.md
├── AGENTS.md                        # short: section 0 rules + current phase + commands
├── docs/
│   ├── BCOSMOS_SPEC.md              # this file
│   ├── architecture.md  data-model.md  api.md  events.md
│   ├── ai-design.md  evaluation.md  rule-catalog.md
│   ├── security-and-privacy.md  limitations.md  development-setup.md
│   └── decisions/ADR-0001-...md
├── backend/
│   └── src/main/java/com/bcosmos/backend/
│       ├── web/            controllers, DTOs, validation, error mapping
│       ├── repository/     registration, SafePathValidator, file discovery
│       ├── jobs/           AnalysisJobService, status, retry
│       ├── graph/          GraphService, ImpactAnalysisService
│       ├── history/        SnapshotComparisonService, FindingTransitionService
│       ├── review/         CommitReviewService (facts, risk signals, AI summary)
│       ├── analytics/      reads agg_* tables, MetricCalculator
│       ├── ai/
│       │   ├── gateway/    LlmClient, ModelRegistry, adapters, UsageRecorder
│       │   ├── rag/        SymbolChunker, EmbeddingService, Retriever
│       │   ├── suggestion/ SuggestionService, PatchValidator, PromptTemplates
│       │   └── eval/       BenchmarkRunner API (reads datasets, calls ai-lab results)
│       ├── persistence/    entities, repositories
│       └── config/
├── analysis-core/                   # shared library (no Spring web), language-agnostic core
│   ├── language/                    # LanguageAdapter interface + registry
│   ├── java/                        # JavaAdapter: JavaParser symbols, PMD rules, patch check
│   ├── typescript/                  # TypeScriptAdapter (phase 10): Tree-sitter, ESLint
│   ├── quality/                     # FindingNormalizer, fingerprint (language-independent)
│   └── model/                       # Symbol, Relationship, Finding records
├── analysis-worker/                 # Spring Boot Kafka consumer using analysis-core
├── miner/                           # JGit crawler + Kafka producer
├── spark-jobs/
│   ├── streaming/findings_to_bronze.py
│   ├── batch/{silver_clean,rule_distribution,time_to_fix,hotspots,quality_trends,fix_triples}.py
│   └── schemas/
├── ai-lab/
│   ├── datasets/                    # split + export scripts, dataset cards
│   ├── eval/                        # harness, metrics, report generator
│   ├── finetune/                    # QLoRA training notebooks (Colab/Kaggle), evaluation, optional model hosting
│   └── prompts/                     # versioned prompt templates
├── frontend/src/app/features/
│   repositories/ quality/ code-cosmos/ impact/ history/ commit-review/ analytics/ ai-review/ model-compare/ benchmark/ settings/
├── infrastructure/docker-compose.yml
├── sample-data/                     # fixture repo + tiny corpus
└── scripts/
```

`analysis-core` is the key clean-architecture decision: the backend (single-repo flow) and the worker (corpus flow) reuse exactly the same parsing and quality code, and everything language-specific lives behind one interface.

### 3.1 Language adapters

```java
interface LanguageAdapter {
    String languageId();                                    // "java", "typescript"
    boolean supports(Path file);
    ParsedFile parse(String source);                        // syntax tree or parse errors
    List<Symbol> extractSymbols(ParsedFile file);
    List<Relationship> extractRelationships(ParsedFile file, ProjectContext ctx); // best effort
    List<Finding> analyze(ParsedFile file, RuleSet rules);  // PMD for Java, ESLint for TypeScript
    String normalizeSnippet(String snippet);                // used by the fingerprint
    PatchCheckResult checkPatch(String before, String after, Finding target);
}
```

| Language-independent (written once) | Per language (written for each adapter) |
|---|---|
| Kafka pipeline, Spark jobs | Parser and symbols |
| Database, API, frontend | Rule tool integration |
| LLM gateway, RAG, evaluation | Relationships (who calls who) |
| Fingerprint logic, finding transitions | Snippet normalization, patch check |

**Support levels** (be honest in the UI about which level a language has):
- **Level 1:** findings + AI explanation + fix validation.
- **Level 2:** Level 1 + symbols (classes, functions) and file structure.
- **Level 3:** Level 2 + relationships and impact analysis. This is the hardest part and differs a lot per language.

Java targets Level 3. TypeScript targets Level 1–2. Events, tables and API responses carry a `language` field from day one.

---

## 4. Domain and data model

### 4.1 Core tables (Liquibase)

| Table | Key columns |
|---|---|
| `repository` | id, name, canonical_local_path (private), source_type (LOCAL / MINED), primary_language, remote_url, default_branch, license_spdx, status |
| `analysis_run` | id, repository_id, commit_hash, status, stage, parser_version, analyzer_version, ruleset_hash, schema_version, files_discovered / parsed / failed, error_code, started_at, finished_at |
| `job` | id, run_id, type, status, attempt, error_summary, timestamps |
| `commit_record` | id, repository_id, hash, parent_hashes[], committed_at, author_hash (hashed), changed_java_files |
| `file_record` | id, run_id, relative_path, language, content_hash, line_count, parse_status |
| `symbol` | id, run_id, file_id, kind, name, qualified_name, start/end line+col, signature_hash, parent_id |
| `relationship` | id, run_id, source_id, target_id (nullable), type, resolution_status, confidence, evidence_file, evidence_line |
| `finding` | id, run_id, file_id, tool, tool_version, rule_id, original_severity, severity, message, start/end line, symbol_id, **fingerprint**, triage_status |
| `finding_transition` | from_run_id, to_run_id, from_finding_id, to_finding_id, type, confidence, reason |
| `metric_snapshot` | run_id, scope_type, scope_key, metric, value, calc_version |
| `commit_review` | id, repository_id, from_commit, to_commit, facts_json, risk_signals_json, ai_summary (nullable), model_id (nullable), created_at |
| `code_chunk` | id, run_id, symbol_id, text, token_count, **embedding vector(N)** (HNSW index), embed_model |
| `llm_provider` | id, code (unique, immutable), label, protocol (GEMINI / OPENAI_COMPATIBLE), base_url, api_key_env, api_key_ciphertext (encrypted, null when the key comes from the environment), api_key_hint, api_key_required |
| `llm_model` | id, provider_id (FK → `llm_provider`), model_name, kind (HOSTED / FINETUNED), enabled, context_window, cost_per_1k_in/out, notes |
| `suggestion` | id, finding_id, model_id, prompt_version, request_hash, response_json, diff, status, validation_json, latency_ms, tokens_in/out, created_at |
| `benchmark_run` | id, dataset_version, model_id, prompt_version, metrics_json, sample_size, created_at |
| `agg_*` | Spark gold outputs, loaded by batch jobs (see section 6) |

### 4.2 Fingerprint (finding identity across commits)
`sha256(ruleId + enclosingSymbolQualifiedName + normalizedFlaggedSnippet)`. Line numbers are deliberately excluded. Matching carries a confidence score and may report `UNCERTAIN`. A finding is never marked RESOLVED if the file failed to parse or the rule was disabled in the later run.

### 4.3 Severity scale
`INFO < LOW < MEDIUM < HIGH < CRITICAL`, with the original tool severity preserved and the mapping documented in `rule-catalog.md`.

---

## 5. Kafka design

### 5.1 Topics

| Topic | Key | Producer → Consumer | Purpose |
|---|---|---|---|
| `repo.analysis.requested.v1` | repositoryId | backend → worker | Full analysis of a registered local repo |
| `repo.commit.discovered.v1` | repositoryId | miner → worker | One event per commit to analyze (changed Java files only) |
| `analysis.findings.v1` | repositoryId | worker → Spark streaming | Findings per (commit, file) version |
| `*.dlq.v1` | same | any → ops | Poison messages with error context |

Keying by `repositoryId` keeps per-repo ordering while many repos are processed in parallel by the consumer group. Partitions ≥ intended worker count.

### 5.2 Event envelope

```json
{
  "eventId": "uuid",
  "eventType": "analysis.findings",
  "schemaVersion": 1,
  "occurredAt": "2026-01-01T00:00:00Z",
  "correlationId": "uuid",
  "repositoryId": 42,
  "payload": { }
}
```

`analysis.findings` payload: `commitHash`, `parentHash`, `filePath`, `contentHash`, `side` (`BEFORE` | `AFTER`), `analyzerVersion`, `rulesetHash`, `parseStatus`, `findings[]` (ruleId, severity, message, startLine, endLine, symbol, fingerprint, snippet).

### 5.3 Delivery semantics
At-least-once delivery with **idempotent sinks**. Postgres writes upsert on `(repository_id, commit_hash, file_path, side, analyzer_version, ruleset_hash)`. Retries use bounded backoff, then the DLQ. Tests cover duplicate delivery and consumer restart.

---

## 6. Spark design

### 6.1 Jobs

| Job | Type | Input → Output |
|---|---|---|
| `findings_to_bronze` | Structured Streaming | Kafka `analysis.findings.v1` → Parquet bronze (partitioned by repository, date); checkpointed |
| `silver_clean` | Batch | Bronze → deduplicated, schema-validated silver |
| `rule_distribution` | Batch | Silver → findings per rule / severity / repo, per KLOC |
| `time_to_fix` | Batch | Silver → per-rule distribution of commits-until-resolved |
| `quality_trends` | Batch | Silver → finding density over time per repo |
| `hotspots` | Batch | Silver + commit churn → ranked files with component scores |
| `fix_triples` | Batch | Silver → (before code, finding, after code, diff) dataset |
| `load_gold` | Batch | Gold Parquet → Postgres `agg_*` (the backend never queries Spark) |

Optional streaming aggregate: rolling per-repo finding counts written to Postgres via `foreachBatch`, for a live ingestion progress view.

### 6.2 Fix-triple extraction rules
A triple is kept only if:
- the file differs between parent and commit,
- the target finding fingerprint exists BEFORE and not AFTER,
- the diff is small (configurable, e.g. ≤ 30 changed lines),
- no new finding of the same rule appears,
- both versions parse.

Splits are **by repository** (not by commit) to prevent leakage. Output is versioned (`dataset_version`), with a dataset card (counts, rules covered, licenses, filters).

### 6.3 Hotspot score (heuristic)
```
risk = w1·norm(complexity) + w2·norm(churn) + w3·norm(dependency_impact) + w4·norm(open_findings)
```
Normalization is percentile rank within the repository. Weights are configurable, component contributions are always shown, and the UI states it is a triage heuristic.

### 6.4 Benchmark the pipeline
Record: repo count, commits, files, finding rows, Parquet size, worker count, wall time, rows/sec, machine specs, and a single-process baseline for comparison. Never claim scale without naming the dataset.

---

## 7. AI design

### 7.1 LLM gateway

```java
interface LlmClient {
    ChatResult chat(ChatRequest req);                       // free text
    <T> StructuredResult<T> structured(StructuredRequest<T> req); // JSON schema output
    List<float[]> embed(List<String> texts);
}
```

Adapters are selected by a provider's **protocol**, and a provider is a row in `llm_provider`, not a constant: **Gemini** (`GEMINI` protocol), and a generic **OpenAI-compatible HTTP** protocol that covers **OpenAI**, **Anthropic** (its OpenAI-compatible layer), DeepSeek, Groq, OpenRouter and local servers such as Ollama or vLLM. Each row carries its base URL and its credential — an environment variable name, or a key the user pastes in Settings and the backend encrypts — so a new vendor or a second account is a data change, never a deployment (ADR 0038). Free tiers have limits that change, so check them before you rely on them. In production use paid keys. A fine-tuned model registers as `kind = FINETUNED` once it is served somewhere (see 7.5).

Gateway responsibilities (this is the real AI-engineering work):
- Model registry and per-request model selection (`modelId` in the API; default in settings).
- Provider registry (`llm_provider`, `GET/POST/PUT/DELETE /api/providers`): which endpoints exist, which protocol
  each speaks and where its key comes from — maintained as data, with the credential encrypted at rest and never
  returned.
- Timeouts, retries with backoff, and cancellation.
- Usage recording: tokens, latency, estimated cost, per suggestion.
- Secret redaction before any prompt leaves the process; **hosted providers are opt-in per model**.
- Structured output: JSON schema in the prompt, parse, validate, and retry once with the validation error.
- Prompt templates in versioned files (`ai-lab/prompts/`), referenced by `prompt_version`.

### 7.2 RAG over code
1. **Chunking by symbol:** one chunk per method/class from JavaParser, with a header (qualified name, file, signature). No arbitrary character splits.
2. **Embeddings:** a hosted embedding model through the gateway (`embed()`); store in `code_chunk.embedding` with an HNSW index. Vector size depends on the model, so store `embed_model` and re-embed if you change it.
3. **Retrieval for a finding** (bounded to a token budget):
   - the enclosing method (exact, from the symbol table),
   - direct callers and callees from the relationship table,
   - top-k semantically similar chunks,
   - the rule description and examples from the rule catalog.
4. Every retrieved item carries file and line range, so the response can cite it.

### 7.3 Suggestion flow

```
finding → retrieve evidence → build prompt → model (structured JSON)
        → validate schema → validate evidence refs exist
        → apply diff in memory → re-run PMD on patched file
        → verdict: {schemaOk, evidenceOk, diffApplies, parses, targetFindingGone, newFindings[]}
        → store → show in UI
```

Response schema: `summary`, `problem_explanation`, `evidence[]` (file, lines), `proposed_diff`, `risks[]`, `tests_to_run[]`, `uncertainties[]`.

Rules: never invent methods or tests; code and comments are treated as **data**, not instructions (prompt-injection defense); the model has no shell, filesystem or network access; patches are never applied automatically.

Optional stronger check: compile in a Docker sandbox with no network, only when the repo's dependencies are available. Otherwise the verdict states "compile not verified".

### 7.4 Evaluation harness (`ai-lab/eval`)
- **Dataset:** the held-out test split of fix triples (never used in fine-tuning), plus a small hand-reviewed set for explanation quality.
- **Automatic metrics per model and prompt version:**
  - schema-valid rate
  - diff-applies rate
  - parses rate
  - **target-finding-resolved rate** (primary)
  - no-new-findings rate
  - median diff size, latency, tokens, cost
- **Conditions compared:** no-retrieval vs RAG; base vs fine-tuned; hosted model A vs B vs C (including free-tier vs paid).
- **Manual review:** 30–50 samples scored on correctness and actionability, with sample size and limitations stated.
- **Caveat to document:** "finding gone" does not prove behavior preserved. It is a necessary, not sufficient, check.
- Output: a report (Markdown + JSON) stored as `benchmark_run`, shown as a leaderboard in the UI.

### 7.5 Fine-tuning (`ai-lab/finetune`)
- **Task:** input = finding + enclosing code (+ small retrieved context) → output = minimal diff + short explanation.
- **Base model:** a small open code model (about 1.5B–7B parameters).
- **Method:** QLoRA with Hugging Face `transformers`/`peft`/`trl`, trained on free Colab/Kaggle GPUs.
- **Data:** training split of fix triples only; evaluation on held-out repositories.
- **Serving (no local GPU needed):** evaluate the merged model inside Colab/Kaggle for the benchmark. To use it in the app, host it on a rented GPU or a managed inference endpoint for a short time and register it in `llm_model`. If you skip hosting, the benchmark results still count.
- **Success criterion:** reported honestly against baselines on the same benchmark. If it does not beat the base model with RAG, document why. That is still a valid result and a good interview story.
- **Licensing:** the corpus must use permissively licensed repositories. Redistribute only derived metadata and the adapter, never raw code, and record licenses in the dataset card.

---

### 7.6 Commit Review (for scrum masters and reviewers)

**Input:** two commits or a range. New commits in a local repo are found by polling git (no webhooks) and flow through the same `repo.commit.discovered.v1` topic.

**Output, in three layers:**
1. **Facts (no AI):** files and symbols changed, findings NEW / RESOLVED / PERSISTENT, complexity change, dependents affected (from the graph), unresolved edges.
2. **Risk signals (rules, shown with their parts):** touches a high-risk file, adds HIGH findings, many dependents, main code changed but no test files changed (heuristic, labeled as such).
3. **AI summary (optional, model selectable):** short plain summary, "what to check", "tests to run". Every claim cites file and lines, and cited references are verified to exist.

**Honest limits:** BCosmos does not promise to find every logic bug. "Coming dangers" means fact-based warnings (for example "complex file, changes often, many dependents"), not predictions. With AI turned off, layers 1 and 2 still work.

---

## 8. API (implement per phase)

| Endpoint | Purpose |
|---|---|
| `POST /api/repositories` | Register a local repo |
| `GET /api/repositories` | List, paginated |
| `POST /api/repositories/{id}/analyses` | Start analysis → run ID |
| `GET /api/analyses/{id}` | Status, stage, counts, errors |
| `GET /api/analyses/{id}/findings` | Filtered, paginated findings |
| `GET /api/analyses/{id}/graph` | Bounded subgraph (server-side depth/node/edge limits) |
| `GET /api/symbols/{id}/{dependencies,dependents,impact}` | Graph queries |
| `GET /api/analyses/compare?from=&to=` | Snapshot comparison |
| `GET /api/repositories/{id}/commits` | Commits with review status |
| `POST /api/repositories/{id}/commit-reviews` | Body: `fromCommit`, `toCommit`, optional `modelId` |
| `GET /api/commit-reviews/{id}` | Facts, risk signals, AI summary |
| `GET/POST /api/models`, `PUT/DELETE /api/models/{id}` | Model registry: list, register any model id on a provider, edit, remove (the default row of a capability cannot be deleted) |
| `GET/POST /api/providers`, `PUT/DELETE /api/providers/{id}` | Provider registry: endpoint, protocol, credential (key never returned; 409 while models reference the row) |
| `POST /api/findings/{id}/explanations` | Phase 2 only: explain a finding with a chosen `modelId` (replaced by suggestions in Phase 3) |
| `POST /api/findings/{id}/suggestions` | Body: `modelId`; returns suggestion |
| `POST /api/findings/{id}/compare-models` | Body: `modelIds[]`; side-by-side suggestions |
| `POST /api/suggestions/{id}/review` | Accept / reject / defer |
| `POST /api/corpus/ingest` | Start mining a list of repo URLs |
| `GET /api/corpus/status` | Pipeline progress (from Postgres) |
| `GET /api/analytics/*` | Reads `agg_*` tables |
| `GET /api/benchmarks` | Leaderboard |

Rules: DTOs only (never entities), pagination everywhere, consistent errors with correlation IDs, no absolute paths in responses.

---

## 9. Frontend

| Screen | Content |
|---|---|
| Repositories | Register, analyze, run status |
| Quality | Findings table, filters, source preview, triage |
| Code Cosmos | Cytoscape 2D graph, search, cluster by package, progressive expansion, table fallback, optional AI summary of a package/class for onboarding |
| Impact | Paths, affected symbols, unresolved edges flagged |
| AI Review | Evidence, explanation, diff, validation verdict, accept/reject/defer |
| Model Compare | Same finding across several models, with latency/cost/verdicts |
| History | Snapshot selection and comparison |
| Commit Review | Commit list, facts, risk signals, optional AI summary, links to evidence |
| Analytics | Corpus charts from Spark; every chart drills down to records |
| Benchmark | Leaderboard and per-condition breakdown |
| Settings | AI master switch, provider registry (add/edit/delete an endpoint, paste a key — encrypted, never sent back), model registry |

Requirements: loading/empty/error states, snapshot shown on every view, severity conveyed by text/icon as well as color, no fabricated metrics while a run is in progress.

---

## 10. Security and privacy

- Canonicalize paths, restrict to user-approved roots, never follow symlinks outside the root, never run repo scripts.
- Limits on file size/count, parse time and output size.
- Absolute paths and source code are not logged.
- All LLM use is through hosted APIs, so code snippets leave the machine. Each model is opt-in with a visible warning, only the needed snippets are sent (never whole repos), and secrets are redacted from prompts and logs. The whole AI layer can be turned off; analysis still works.
- API keys come from environment variables or a secrets store, never committed. A key pasted into the Settings
  screen is stored encrypted (AES-256-GCM under `BCOSMOS_AI_SECRET_KEY` from the environment, the provider code as
  associated data): the plaintext is never a column, never logged and never returned — only a credential source and
  the last four characters (ADR 0038).
- Deleting BCosmos metadata never touches the original repository.
- The miner only clones public repositories from an allowlisted host and uses JGit (no hooks, no builds).

---

## 11. Testing

| Level | What |
|---|---|
| Unit | Path validation, ignore rules, symbol extraction, fingerprinting, severity mapping, transition classification, patch application, schema validation, hotspot math |
| Fixture repo | Interface/impl, inheritance, controller→service→repository, a known smell, an empty catch, a syntax-error file, ignored folders, **two commits (one finding fixed, one introduced)** |
| Integration (Testcontainers) | Postgres+pgvector, Kafka: register → analyze → findings; duplicate delivery; worker restart; DLQ path; migrations from a clean DB |
| Language adapter | One shared contract test suite that every adapter (Java, TypeScript) must pass |
| Spark | Small known Parquet inputs with hand-computed expected aggregates |
| AI | Mock `LlmClient` for flow tests; golden prompts; schema-violation retry; prompt-injection fixture (a comment saying "ignore previous instructions") |
| Benchmark | Reproducible with fixed seeds, versioned dataset and prompt version |

---

## 12. Roadmap

Sizes: S ≈ days, M ≈ 1–2 weeks, L ≈ 2–4 weeks (depends on your pace).

**Detailed per-phase scope and agent prompts are in `docs/PHASES.md`** (source file `BCOSMOS_PHASES.md`). Use that file when you start a phase with an AI agent.

### Phase 0: Foundation (S)
Create the Bitbucket project `BCosmos` with one repository `bcosmos` (monorepo, see ADR 0014). Gradle/Maven multi-module (`backend`, `analysis-core`, `analysis-worker`, `miner`), Angular app, Compose with Postgres+pgvector, Liquibase, health endpoint, ADRs, `AGENTS.md`.
**Done when:** everything starts from the README commands, and tests/builds run.

**Backend initial dependencies** (create with start.spring.io: Java 21, Jar, Group `com.bcosmos`, Artifact `backend`, Package `com.bcosmos.backend`, latest stable Spring Boot, never a SNAPSHOT or milestone):

| Select in Initializr | Why |
|---|---|
| Spring Web | REST controllers, `RestClient` for LLM calls |
| Validation | Request DTO validation |
| Spring Data JPA | Persistence |
| PostgreSQL Driver | Database driver |
| Liquibase Migration | Schema migrations (ADR 0009) |
| Spring for Apache Kafka | Producer for `repo.analysis.requested.v1` |
| Spring Boot Actuator | Health and metrics |
| Testcontainers + Spring Boot Testcontainers | Postgres and Kafka in integration tests |
| Docker Compose Support (optional) | Starts `docker-compose.yml` with the app |

Add manually after generating the project:

| Dependency | Why | Scope |
|---|---|---|
| `analysis-core` (own module) | Languages, symbols, findings (JavaParser and PMD live there, not in `backend`) | compile |
| pgvector support for Hibernate/JDBC | Store and search embeddings (check the current artifact at implementation time) | compile |
| springdoc-openapi | OpenAPI docs for the REST API | compile |
| JGit | Read commits for Commit Review polling | compile |
| Resilience4j (or Spring Retry) | Timeouts and retries in the LLM gateway | compile |
| ArchUnit | Test that module boundaries are respected (clean architecture) | test |
| WireMock | Fake LLM providers in adapter tests | test |

Deliberately **not** added now:
- **Spring AI:** you build the `LlmClient` gateway yourself to learn it (section 7.1).
- **Spring Security:** authentication is out of scope (section 1.3).
- **Lombok:** use Java records and plain classes. Optional if you already use it.

`analysis-worker` and `miner` get their own small Spring Boot projects: `analysis-worker` needs Spring for Apache Kafka, Spring Data JPA, PostgreSQL Driver, Liquibase is **not** needed there (the backend owns migrations), plus `analysis-core`. `miner` needs Spring for Apache Kafka and JGit.

**Frontend init** (inside the existing `frontend/` folder; if it already has its own `.git` from a clone, keep it):

```bash
cd frontend
npx @angular/cli@latest new bcosmos-frontend --directory . --routing --style=scss --ssr=false --skip-git
npm install cytoscape chart.js
npm install -D @types/cytoscape
# UI components: install PrimeNG by following its current install guide
```

Notes: `--skip-git` stops Angular from creating a new Git repository and keeps the existing one. If the CLI refuses because the folder is not empty, add `--force`. Use standalone components and lazy-loaded routes per feature (section 3 `frontend/src/app/features/`). Requires a Node.js version supported by the installed Angular CLI.

**Other modules init** (use the same build tool as `backend`; local ports: `backend` 8080, `analysis-worker` 8081, `miner` 8082):

| Module | How | Settings and dependencies |
|---|---|---|
| `analysis-worker` | start.spring.io | Java 21, Jar, Group `com.bcosmos`, Artifact `analysis-worker`, Package `com.bcosmos.worker`. Select: Spring for Apache Kafka, Spring Data JPA, PostgreSQL Driver, Spring Boot Actuator, Testcontainers, Spring Web (only so `/actuator/health` works). Add `analysis-core` manually. No Liquibase (the backend owns migrations). |
| `miner` | start.spring.io | Same settings, Artifact `miner`, Package `com.bcosmos.miner`. Select: Spring for Apache Kafka, Spring Boot Actuator, Testcontainers, Spring Web (health only). Add JGit manually: `org.eclipse.jgit:org.eclipse.jgit` version `7.8.0.202609011348-r` (latest release tag, checked October 2026; if Maven cannot find it use `7.7.1.202607240634-r`). Public repositories are cloned over HTTPS, which needs no extra JGit module. Use the same version in `backend` for Commit Review. No database. |
| `analysis-core` | Manual (plain Maven jar, no Spring) | Group `com.bcosmos`, Artifact `analysis-core`, `maven.compiler.release` 21. Create `analysis-core/pom.xml` (next to `src/`). Dependencies and pinned versions (latest release tags, checked October 2026; if Maven cannot find one, use the previous release): `com.github.javaparser:javaparser-symbol-solver-core` 3.28.2, `net.sourceforge.pmd:pmd-java` 7.28.0, `org.junit.jupiter:junit-jupiter` 6.1.3 (test), `org.assertj:assertj-core` 3.27.7 (test). Packages under `com.bcosmos.core`: `language`, `java`, `quality`, `model`. |
| `spark-jobs` | Manual (Python venv) | Python 3.11 or 3.12, `pyspark`, `pytest`. Installed so far: `pyspark` 4.2.0, `pytest` 9.1.1 (see `spark-jobs/requirements.txt`); needs Java 17, 21 or 25 on the PATH. Folders: `streaming/`, `batch/`, `schemas/`, `tests/`. On Windows run Spark jobs in Docker or WSL2 (avoids Hadoop winutils problems). |
| `ai-lab` | Manual (Python venv) | Python 3.11 or 3.12, `pandas`, `pyarrow`, `pydantic`, `httpx`, `pytest`, `jupyter`. Folders: `datasets/`, `eval/`, `finetune/`, `prompts/`. Fine-tuning libraries (`transformers`, `peft`, `trl`) are installed only inside Colab/Kaggle notebooks, not locally. |

Adding `analysis-core` as a dependency: `com.bcosmos:analysis-core:0.0.1-SNAPSHOT`. With one repository (option A) it resolves through the root multi-module build. With separate repositories (option B) run `mvn install` in `analysis-core` first.

**Infrastructure** (`infrastructure/docker-compose.yml`, local development only, never reuse these passwords):

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_DB: bcosmos
      POSTGRES_USER: bcosmos
      POSTGRES_PASSWORD: bcosmos
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bcosmos -d bcosmos"]
      interval: 5s
      timeout: 3s
      retries: 10

  kafka:
    image: apache/kafka:4.3.1
    profiles: ["pipeline"]
    ports:
      - "9092:9092"

volumes:
  pgdata:
```

Run `docker compose up -d` for Postgres only, and `docker compose --profile pipeline up -d` to add Kafka (single node, KRaft, default settings of the official image). Kafka is only needed from Phase 4. If the Kafka tag does not exist, use the latest `4.x` tag from the `apache/kafka` image page. Spring settings for the backend: `spring.datasource.url=jdbc:postgresql://localhost:5432/bcosmos`, user and password `bcosmos`.

### Phase 1: Core slice (M)
SafePathValidator, registration API, `analysis_run`/`job`, file discovery, **`LanguageAdapter` interface with `JavaAdapter` as the only implementation**, JavaParser symbols, PMD adapter, normalized findings, findings API, Angular findings table, fixture repo.
A `JobDispatcher` interface runs jobs in-process in this phase; the Kafka implementation replaces it in Phase 4.
**Done when:** register a local repo → run → see findings with file/line evidence in the browser.

### Phase 2: AI gateway and structured output (M)
`LlmClient`, Gemini adapter (free tier) + one more hosted adapter, model registry, usage recording, JSON-schema output with retry, redaction, a minimal "explain this finding" call.
**Done when:** the same prompt runs on two models, and a schema violation is retried and logged.

### Phase 3: RAG and suggestions (M)
Symbol chunker, embeddings, pgvector retrieval, evidence-cited suggestions, patch validator, AI Review screen.
**Done when:** a suggestion shows cited evidence, a diff and a validation verdict, and works with the model disabled (analysis still works).

### Phase 4: Kafka pipeline (L)
Topics, event envelope, worker consumer group, miner (JGit), idempotent sinks, DLQ, corpus ingest API and status screen.
**Done when:** 20+ repos are ingested in parallel, a duplicate-delivery test passes, and throughput is measured against a single-process baseline.

### Phase 5: Spark (L)
Streaming to bronze, silver cleaning, batch aggregates, `fix_triples`, `load_gold`, Analytics screen.
**Done when:** aggregates match hand-computed fixtures, and a dataset card exists with row counts and sizes.

### Phase 6: Evaluation harness (M)
Dataset splits by repo, metrics, baselines, leaderboard, Model Compare screen.
**Done when:** at least 3 model/prompt conditions are ranked on the held-out set with sample sizes.

### Phase 7: Fine-tuning (M)
QLoRA training on Colab/Kaggle, evaluate against baselines in the notebook, then (optional) host the model and register it in the registry.
**Done when:** the fine-tuned model appears on the leaderboard with an honest write-up.

### Phase 8: Graph, history, polish (M)
Relationships + graph API, Cytoscape view, snapshot comparison, hotspots, README with screenshots, demo recording.
**Done when:** a new user can reproduce the demo from the README.

### Phase 9: Commit Review (M)
Git polling → `repo.commit.discovered.v1`, `CommitReviewService` (facts + risk signals), optional AI summary with model choice, Commit Review screen.
Tip: a "lite" version (NEW/RESOLVED findings between two commits + AI summary) can be built right after Phase 3 if you want the most useful feature earlier.
**Done when:** you pick two commits and get a review where every claim has evidence, and with AI off the facts and risk signals still show.

### Phase 10: Second language, TypeScript (M)
`TypeScriptAdapter` (Tree-sitter + ESLint) at support level 1–2 (findings, symbols, AI explanation; no full call graph). Pass the shared adapter contract tests.
**Done when:** a small Angular repo is analyzed through the same pipeline with no changes to Kafka, Spark, AI or backend code.

---

## 13. Demo script (4–5 minutes)
1. Register a sample Spring Boot repo and watch the run progress.
2. Browse findings and open one with source evidence.
3. Review a new commit (Commit Review), then ask for a fix with two different hosted models in Model Compare. Show the verdicts.
4. Show the corpus pipeline: Kafka lag, workers, rows ingested.
5. Show Spark analytics with the dataset size and runtime.
6. Show the leaderboard including the fine-tuned model, and what worked and what didn't.
7. Close with limitations.

---

## 14. Risks

| Risk | Mitigation |
|---|---|
| Scope creep | Strict phases; each phase leaves the app runnable |
| JavaParser symbol resolution gaps (DI, Lombok, generics) | Record unresolved edges, show coverage, spike early in Phase 1 |
| Fingerprint mismatches across commits | Confidence scores, `UNCERTAIN` class, manual spot checks |
| Too few clean fix triples | Broaden rules/repos, relax the diff-size filter, report dataset stats honestly |
| Fine-tuning does not beat the baseline | Report it. Evaluation rigor is the deliverable |
| GPU access | Colab/Kaggle for training; hosted APIs for inference |
| API cost and free-tier limits | Usage recording, per-model budgets, cache by `request_hash`, small benchmark sets first |
| Looks like SonarQube / CodeRabbit | Position as a lab for measured AI review on top of static analysis, not a product competitor |
| License issues with mined code | Permissive licenses only, publish derived data only |
| Infra weight on a weak laptop (Kafka + Spark + Postgres) | Compose profiles `core` / `pipeline`; start with about 20 repos; Spark in local mode |

---

## 15. CV claims (only after the phase is done and tested)
- *Built an event-driven analysis pipeline (Kafka, Spring Boot) processing N commits across M repositories in parallel, with idempotent sinks and dead-letter handling.*
- *Implemented Spark batch and Structured Streaming jobs producing a versioned Parquet lakehouse and a fix-triple dataset of K examples.*
- *Designed a multi-provider LLM gateway (Gemini, OpenAI, Anthropic) with RAG over AST-chunked code, automatic patch validation, and a benchmark harness comparing hosted and QLoRA-fine-tuned models (resolved-rate X% vs Y%).*
- *Built an evidence-based commit review feature (facts, risk signals, optional AI summary) for reviewers and scrum masters.*
- *Designed a language-adapter architecture and proved it by adding TypeScript without changing the pipeline.*

---

## 16. Open decisions for you
1. Maven or Gradle for the multi-module build.
2. Spark jobs in PySpark (default here) or Java/Scala.
3. Which providers first (suggested: Gemini for free testing plus one paid provider).
4. Corpus selection: which 20–50 permissively licensed Java repos to start, and the commit window per repo.
5. Budget: monthly limit for paid APIs and (optional) short GPU rental to host the fine-tuned model.
