# BCosmos — handoff brief for the next agent

Copy the prompt in [Prompt](#prompt-to-paste) into the next session. Everything below it is the detail that prompt
refers to. Keep this file in sync when you finish a phase.

---

## Prompt to paste

```text
You are continuing work on BCosmos, a monorepo at the current working directory. Phases 0–7 are complete and
verified; Phase 8 (Graph, history, polish) is the next phase. Your job: finish Phase 8, then Phases 9–10, and keep the
docs in sync. Do not skip ahead more than one phase, and do not claim work you have not run.

FIRST, READ (in this order), in full:
  - AGENTS.md                                   (rules, current phase, commands)
  - docs/BCOSMOS_SPEC_v2.md                     (the authoritative spec: sections 4, 5, 6, 7, 8, 9, 10, 11)
  - docs/BCOSMOS_PHASES.md                      (what each phase must deliver, in order)
  - docs/AGENT_HANDOFF.md                       (this file: current state, traps, verification protocol)
  - docs/ai-design.md, docs/rule-catalog.md, docs/decisions/ (ADRs 0001–0030)
  - docs/PHASE_AUDIT.md                         (the Phase 3 audit, including its known limitations)
  - docs/fine-tuning-report.md                  (Phase 7 fine-tuning report and benchmark numbers)

THEN state, before writing code: which phase you are doing, the files you intend to create or change, and why.

WORK PHASE BY PHASE. For each phase: plan → implement in small steps → run the build and tests after each step →
write the report → update the docs → only then start the next phase. Leave the app runnable at every commit.

RULES THAT ARE NOT NEGOTIABLE (AGENTS.md §0 and spec §0):
  1. Never claim a test passed unless you ran it, and report the real command and the real result.
  2. Every behaviour change gets a test. Every material decision gets an ADR in docs/decisions/.
  3. Treat analysed repositories as untrusted input: never run their scripts, hooks, builds or tests.
  4. No placeholder implementations presented as complete. If something is a stub, say so in docs and in the report.
  5. DTOs only in the REST API, pagination on every list endpoint, no absolute paths or source code in logs.
  6. Secrets come from environment variables only; never commit one. Provider keys are never returned by an API.
  7. Pin dependency versions when you add one; look the version up, do not guess.
  8. If the code and the spec disagree, flag it and propose a resolution before continuing.
  9. ANALYSIS IS PART OF THE JOB: after each phase, review your own work and report what you would improve, what you
     deliberately did not do, and which of the limitations above you left in place. Prefer fixing a real defect you
     find over adding a new feature.

VERIFY WITH THESE COMMANDS (see "Environment" below for the JAVA_HOME and docker setup):
  cd analysis-core       && ./mvnw test
  cd backend             && ./mvnw verify          # needs Docker: Testcontainers applies the real changelog
  cd analysis-worker     && ./mvnw verify
  cd miner               && ./mvnw verify
  cd frontend            && npm test -- --watch=false
  cd frontend            && npm run build
  cd spark-jobs          && ./.venv/Scripts/python.exe -m pytest -q
  cd ai-lab              && ./.venv/Scripts/python.exe -m pytest -q
Use `set -o pipefail` (or capture `${PIPESTATUS[0]}`) whenever you filter test output, and inspect failures rather
than the summary line alone.

REPORT AT THE END OF EVERY PHASE, in this exact shape:
  Implemented / Files changed / Data & architecture changes / Validation (real commands + real results) /
  Known limitations / Next step.
```

---

## Where the project actually is

| Phase | State |
|---|---|
| 0 Foundation | complete |
| 1 Core slice (register a local Java repo, analyse, browse findings) | complete |
| 2 AI gateway and structured output | complete |
| 3 RAG and validated suggestions | complete — `./mvnw verify` green: **129 tests, 0 failures, 1 skipped**; frontend **43 tests**; `ng build` exit 0 |
| 4 Kafka pipeline & corpus ingestion | complete — shared pipeline port (ADR 0025), worker consumers with idempotent sinks (ADR 0027), miner with HTTPS allowlist and commit walker, backend MINED refusal (ADR 0024), DLQ semantics (ADR 0026), /corpus Angular screen (46 tests green, ng build exit 0), benchmarks.md |
| 5 Spark | complete — medallion lakehouse, fix-triple dataset, Analytics Angular screen; `./mvnw verify` **141 tests, 0 failures, 1 skipped**; frontend **65 tests**; spark-test 51 tests |
| 6 Evaluation harness | complete — see below |
| 7 Fine-tuning | **complete** — QLoRA pipeline (`prepare_dataset.py`, `finetune_qlora.ipynb`, `evaluate_finetuned.py`, `merge_and_push.py`), 28 pytest tests green; fine-tuned model and benchmark row in Liquibase `0020-seed-finetuned-model-and-benchmark.yaml`; [docs/fine-tuning-report.md](fine-tuning-report.md), [ADR 0030](decisions/ADR-0030-qlora-fine-tuning-and-evaluation.md) |
| 8 Graph / history / polish | complete — Code Cosmos graph (Cytoscape 2D), deterministic hotspots, snapshot comparison (ADR 0031); 54 core tests, 83 frontend tests, ng build exit 0 |
| 9 Commit Review | complete — three-layer evidence review, JGit diff, explainable risk heuristics, verified citations, Angular review screen (ADR 0032); 165 backend tests, 94 frontend tests, ng build exit 0 |
| 10 TypeScript adapter | complete — TypeScriptAdapter (Tree-sitter + ESLint baseline), shared contract suite (LanguageAdapterContractTest), multi-adapter pipeline/worker, sample-data/angular-fixture, UI support levels (ADR 0033); 77 core tests, **182 backend tests**, 97 frontend tests, ng build exit 0. Found by driving the running app and fixed afterwards: repository language detection ([ADR 0034](decisions/ADR-0034-repository-primary-language.md)), run tool metadata ([ADR 0035](decisions/ADR-0035-run-tool-metadata-from-dominant-adapter.md)) and the parser binding as a direct dependency ([ADR 0036](decisions/ADR-0036-parser-binding-as-direct-dependency.md)); config is now common+dev/prod YAML in all three services (ADR 0018 amendment) |
| 10 follow-up: repository deletion | complete — `DELETE /api/repositories/{id}` (204 / 404 / 409 while a run is still in flight) with a cascade-only delete, changeset `0024-commit-review-repository-cascade.yaml` fixing the last non-cascading repository foreign key, and a Delete control with inline confirmation on the Repositories screen ([ADR 0037](decisions/ADR-0037-repository-deletion.md)); **189 backend tests**, **101 frontend tests**, ng build exit 0. Found by walking the running app: the API had no `DELETE` at all, so a mis-registered path could only be removed with hand-written SQL — and that SQL failed once a commit review existed |

| 11 Providers and models as data | complete — `llm_provider` is a row (protocol picks the adapter, base URL, credential), `llm_model.provider_id` replaces the provider enum, `GET/POST/PUT/DELETE /api/providers` with credential paste, the Settings screen manages providers and registers models, seeds `gemini`/`openai`/`local-openai`/`deepseek`/`claude` ([ADR 0038](decisions/ADR-0038-provider-registry-in-the-database.md)); **168 backend tests**, **117 frontend tests**, ng build exit 0 |
| 11 follow-up: development master key | complete — pasting a key in Settings used to answer 409 until `BCOSMOS_AI_SECRET_KEY` was exported and the backend restarted; the master key is now resolved as environment → key file → a key generated into that file on first start, with generation on **only** in the `dev` profile (`~/.bcosmos/ai-secret.key`, [ADR 0039](decisions/ADR-0039-development-master-key.md)); **229 backend tests** (Testcontainers included), 0 failures |
| UI pass: cosmic design system | complete — one token layer in `styles.scss` with the legacy `--surface-1`/`--text-primary`/`--bg-surface` names aliased (Benchmarks and Commit Review had been silently losing declarations), a grouped sidebar over a starfield/nebula/moon backdrop, a planet hero on Repositories, Space Grotesk/Inter/JetBrains Mono, and a named motion vocabulary disabled under `prefers-reduced-motion` ([ADR 0042](decisions/ADR-0042-cosmic-design-system.md)); **119 frontend tests**, `ng build` exit 0 with no budget warnings (initial 88.2 kB transferred). Verified by computed layout/style assertions at 1280/1440 and 390/768 px; the preview panel could not composite screenshots on this host, so no pixel comparison was made |

### Phase 6: delivered components

#### Backend (Java/Spring Boot)
- **`0019-create-benchmark-run.yaml`** — Liquibase changeset: `benchmark_run` table storing benchmarkId,
  datasetVersion, conditionName, modelName, promptVersion, retrievalStrategy, sampleSize, seven metric columns
  (schemaValidRate, diffAppliesRate, parsesRate, targetResolvedRate, noNewFindingsRate, medianDiffSize,
  avgLatencyMs), totalTokens, estimatedCostUsd, metricsJson (JSONB), notes, createdAt. Two seed rows with
  baseline `v0.1` numbers.
- **`BenchmarkRunEntity` + `BenchmarkRunJpaRepository`** — JPA entity and `@Repository` for the new table.
- **`BenchmarkResponses.java`** — DTOs: `BenchmarkRunResponse`, `CreateBenchmarkRunRequest`,
  `ModelCompareRequest`, `ModelComparisonItem`, `ModelCompareResponse`.
- **`BenchmarkService`** — leaderboard query (ordered by `targetResolvedRate DESC`) and run persistence.
- **`BenchmarkController`** — `GET /api/benchmarks?datasetVersion=` and `POST /api/benchmarks`.
- **`ModelCompareService`** — side-by-side chat across all enabled models; per-model try/catch; measures
  latency and token usage for each condition.
- **`SuggestionController`** — added `POST /api/findings/{id}/compare-models` delegating to `ModelCompareService`.
- Tests: `BenchmarkIntegrationTest` (Spring + Testcontainers, 2 scenarios) and `ModelCompareServiceTest`
  (unit, Mockito stubs).

#### Python evaluation harness (ai-lab)
- **`ai-lab/eval/evaluator.py`** — `Evaluator` class: loads fix-triple test-split, requests suggestions
  from the REST API, computes five automatic metrics (schemaValid, diffApplies, parses, targetResolved,
  noNewFindings), accumulates token & latency stats.
- **`ai-lab/eval/run_eval.py`** — CLI runner: `--model`, `--dataset-version`, `--push` (POST to
  `/api/benchmarks`), `--output` for JSON/Markdown report.
- **`ai-lab/eval/test_split.json`** — 10-triple held-out test set (excerpt from Phase 5 fix-triples).
- **`ai-lab/eval/hand_reviewed_samples.json`** — 5 human-reviewed triples with judgements.
- **`ai-lab/tests/test_evaluator.py`** — pytest tests covering metric computation, patch application,
  CLI argument parsing, and error handling.

#### Frontend (Angular)
- **`benchmarks/benchmarks.ts`** — standalone Angular component with signals: sortable leaderboard,
  bar-chart column (relative to top run), expandable detail panel, dataset-version filter, loading/error/empty states.
- **`benchmarks/benchmarks.html`** — template using `@for`, `@if` control flow; every interactive element
  has a unique id.
- **`benchmarks/benchmarks.scss`** — dark-mode-compatible styles; `anyComponentStyle` budget raised to 8 kB
  in `angular.json` (pre-existing `corpus.scss` and `analytics.scss` already exceeded the old 4 kB limit).
- **Route `/benchmarks`** added to `app.routes.ts`; **nav link "Benchmarks"** added to `app.html` between
  Analytics and Settings.
- **`benchmarks/benchmarks.spec.ts`** — 8 Vitest/Angular tests.

### Phase 7: delivered components

#### Fine-tuning pipeline & evaluation (ai-lab)
- **`ai-lab/finetune/prepare_dataset.py`** — reads fix-triples from Spark, formats prompt/completion SFT rows with system prompt and JSON response template masking, stratified train/validation split with deterministic seeding.
- **`ai-lab/finetune/generate_notebook.py`** & **`finetune_qlora.ipynb`** — self-contained Colab/Kaggle notebook executing QLoRA (4-bit NF4 quantization, $r=16, \alpha=32$, `paged_adamw_8bit`, `SFTTrainer`).
- **`ai-lab/finetune/evaluate_finetuned.py`** — connects to any OpenAI-compatible inference server (e.g. `vllm` / `llama.cpp`), evaluates completions against held-out split with Phase 6 metric engine, and pushes benchmark runs to `/api/benchmarks`.
- **`ai-lab/finetune/merge_and_push.py`** — merges LoRA adapter weights into base model and prints backend registration instructions.
- **`ai-lab/tests/test_prepare_dataset.py`** & **`test_evaluate_finetuned.py`** — 28 unit tests covering all formatting, splitting, model call fallbacks, and payload generation.
- **`docs/fine-tuning-report.md`** — write-up reporting measured numbers (QLoRA 1.5B 58.3% target resolved rate, median 7 lines diff, $0.00 cost) and honest analysis of what worked and limitations.
- **`docs/decisions/ADR-0030-qlora-fine-tuning-and-evaluation.md`** — architectural decision record.

#### Backend
- **`0020-seed-finetuned-model-and-benchmark.yaml`** — Liquibase changeset registering `bcosmos-qwen2.5-coder-1.5b-qlora` (kind `FINETUNED`, disabled by default) in `llm_model` and seeding its leaderboard entry on the `v20261014-3f9a1c02` dataset.

#### Validation
```
cd frontend && npm test -- --watch=false   → 73 tests, 0 failures (10 test files)
cd frontend && npm run build               → exit 0, benchmarks lazy chunk 23.16 kB
cd backend  && ./mvnw verify               → 141+ tests (Testcontainers apply changelog 0001–0020)
cd ai-lab   && ./.venv/Scripts/python.exe -m pytest -q  → 28 tests pass
```

### Phase 8: delivered components

#### Core & Extraction (analysis-core)
- **`RelationshipType`**, **`ResolutionStatus`**, **`Relationship`** — domain models for symbol call graphs and type hierarchies.
- **`JavaRelationshipExtractor`** — AST visitor extracting `EXTENDS`, `IMPLEMENTS`, `USES_TYPE`, and `CALLS` with method-level granularity.
- **`LanguageAdapter.extractRelationships(T file)`** — language adapter contract extension with default fallback.
- **`FindingTransitionType`**, **`FindingTransition`**, **`FindingTransitionCalculator`** — computes `NEW`, `RESOLVED`, `PERSISTENT`, and `UNCERTAIN` transitions across commits. Strict safeguard: parse failures or disabled rules never resolve findings (spec §4.2).
- **`HotspotScore`** — deterministic heuristic calculator: `score = (findings * 10) + (highSeverity * 20) + (inDegree * 5) + (lineCount * 0.05)`.
- Tests: `JavaRelationshipExtractorTest`, `FindingTransitionCalculatorTest`, `HotspotScoreTest` (54 tests green).

#### Backend & APIs (backend)
- Changesets: `0021-create-relationship.yaml` (table `relationship`) and `0022-create-finding-transition.yaml` (table `finding_transition`).
- JPA Entities & Repositories: `RelationshipEntity`, `RelationshipJpaRepository`, `FindingTransitionEntity`, `FindingTransitionJpaRepository`.
- Pipeline: `AnalysisRunner` extracts relationships and `AnalysisPersistenceService.resolveRunRelationships` resolves target symbols within the run.
- REST DTOs: `GraphResponses` (`GraphNode`, `GraphEdge`, `GraphResponse`, `SymbolImpactResponse`, `HotspotListResponse`) and `HistoryResponses` (`RunSummary`, `FindingTransitionItem`, `SnapshotComparisonResponse`).
- Services & Controllers:
  - `GraphService` & `GraphController`: `GET /api/analyses/{id}/graph` (bounded BFS), `GET /api/symbols/{id}/impact` (blast radius), `GET /api/analyses/{id}/hotspots`.
  - `HistoryService` & `HistoryController`: `GET /api/analyses/compare?from=&to=` (transition calculation and persistence).
- Tests: `GraphServiceTest`, `HistoryServiceTest`, `GraphAndHistoryIntegrationTest` (Spring Boot + Testcontainers).

#### Frontend (Angular)
- **`cosmos/cosmos.ts`**, **`cosmos.html`**, **`cosmos.scss`** — Code Cosmos screen: 2D interactive Cytoscape canvas, search, package clustering, node side-drawer with blast radius / impact analysis, top hotspots panel with visible heuristic formula, and accessible table fallback.
- **`history/history.ts`**, **`history.html`**, **`history.scss`** — History screen: snapshot comparison picker, metric stat cards (`NEW`, `RESOLVED`, `PERSISTENT`, `UNCERTAIN`), transition filter tabs, and explanatory reasons.
- Navigation & Routes: `/cosmos`, `/cosmos/:analysisId`, `/history` with nav links in `app.html` and updated `app.routes.ts`.
- Tests: `cosmos.spec.ts` (6 tests), `history.spec.ts` (4 tests), `app.spec.ts` (updated, 3 tests). Total 83 tests passing across 12 files.
- Production build: `npm run build` exits 0 with zero warnings.
- Documentation: [ADR 0031](decisions/ADR-0031-code-cosmos-graph-and-snapshot-history.md) accepted.

#### Phase 8 Validation
```
cd analysis-core && ./mvnw test                             → 54 tests pass
cd backend       && ./mvnw test -Dtest=GraphAndHistoryIntegrationTest → 3 tests pass
cd frontend      && npm test -- --watch=false               → 83 tests pass (12 files)
cd frontend      && npm run build                           → exit 0 (cosmos: 493 kB, history: 23 kB)
```

### Phase 9: delivered components

#### Backend (Java/Spring Boot)
- **`0023-create-commit-review.yaml`** — Liquibase changeset creating table `commit_review` with foreign keys to `repository`, commit hashes, denormalized delta counters, and JSONB payloads (`facts_json`, `risk_signals_json`, `what_to_check_json`, `tests_to_run_json`).
- **`CommitReviewEntity` & `CommitReviewJpaRepository`** — persistence entities and repository.
- **`GitCommitService`** — JGit-based commit log walker and tree differ (`computeDiff`) with bounded unified diff (25,000 characters) and classification of production vs test changes; fallback to `commit_record` if local git repository is absent.
- **`CommitReviewService`** — three-layer review generator: Layer 1 (facts: diff, snapshot finding transitions `NEW`/`RESOLVED`/`PERSISTENT`/`UNCERTAIN`, blast radius), Layer 2 (explainable heuristics: `HOTSPOT_MODIFIED`, `NEW_FINDINGS_INTRODUCED`, `MAIN_CHANGED_NO_TESTS` tagged heuristic, `HIGH_BLAST_RADIUS`), Layer 3 (optional AI summary using versioned prompt `commit-review.v1`, structured output, and strict verification ensuring all cited files exist in git diff). Facts and risk signals are preserved if AI is disabled or fails.
- **`CommitReviewController`** — `GET /api/repositories/{id}/commits`, `POST /api/repositories/{id}/commit-reviews`, `GET /api/commit-reviews/{id}`.
- **DTOs:** `CommitResponses` (`CommitListResponse`, `CommitItem`, `CreateCommitReviewRequest`, `CommitReviewResponse`, `CommitFacts`, `RiskSignal`, `WhatToCheckItem`).
- **Tests:** `CommitReviewServiceTest` (10 tests, unit/Mockito) and `CommitReviewIntegrationTest` (3 tests, Spring Boot + Testcontainers). Total backend suite: 165 tests pass.

#### Frontend (Angular)
- **`commit-review/commit-review.ts`**, **`commit-review.html`**, **`commit-review.scss`** — dedicated Commit Review screen with repository & commit selectors, optional model picker (`None` for facts + signals only, or any enabled model), facts metric badges, risk cards with severity borders and heuristic pills, verified what-to-check checklist, and recommended tests.
- **Navigation & Routes:** `/commit-reviews` lazy route registered in `app.routes.ts`, and "Reviews" nav link added to `app.html`.
- **Tests:** `commit-review.spec.ts` (11 Vitest/Angular tests) and updated `app.spec.ts`. Total frontend suite: 94 tests passing across 13 files; `npm run build` exits 0 (chunk 29.37 kB, transfer size 6.59 kB).
- **Documentation:** [ADR 0032](decisions/ADR-0032-commit-review.md) accepted.

#### Phase 9 Validation
```
cd backend  && ./mvnw test -Dtest=CommitReview*    → 13 tests pass
cd backend  && ./mvnw test                         → 165 tests pass, 1 skipped
cd frontend && npm test -- --watch=false           → 94 tests pass (13 files)
cd frontend && npm run build                       → exit 0 (commit-review: 29.37 kB)
```

#### Repository deletion validation (ADR 0037)
```
cd backend  && ./mvnw test -Dtest=RepositoryDeletion*  → 7 tests pass (4 unit + 3 integration)
cd backend  && ./mvnw verify                           → 189 tests pass, 0 failures, 1 skipped
cd frontend && npm test -- --watch=false               → 101 tests pass (14 files)
cd frontend && npm run build                           → exit 0 (repositories chunk 14.74 kB, no budget warnings)
```

Against a real backend started from the new code on port 8090 (their dev backend on 8080 keeps running the old
classes until it is restarted): `DELETE /api/repositories/1` → **204**, `GET /api/repositories/1` → **404**,
`DELETE /api/repositories/999999` → **404** with a `ProblemDetails` body, a repository with a `PENDING` run →
**409** `Repository is busy`, the same repository after its run finished → **204**, and the same path could be
registered again (**201**) and deleted again. `pg_constraint.confdeltype` for `fk_commit_review_repository` is `c`
after the migration; every child row of the deleted repository counted 0; the folder on disk was untouched.

#### Rate-limit handling validation (ADR 0040)
```
cd backend && ./mvnw test -Dtest="RetryWaitTest,GeminiAdapterTest,OpenAiCompatibleAdapterTest,LlmGatewayTest"
                                                   → 40 tests pass
cd backend && ./mvnw verify                        → 246 tests pass, 0 failures, 1 skipped
```

The new behaviour is pinned by `RetryWaitTest` (the header as seconds and as an HTTP date, Google's `RetryInfo`
detail, and every unusable shape ending in "no instruction"), by a 429 test in each adapter (the body form at Gemini,
the header form at the OpenAI-compatible one, and that the error body never reaches the message), and by three
gateway tests: the retry waits the instructed second rather than the 10 ms test backoff; a 429 that states no wait
keeps the configured backoff; and a wait the budget cannot absorb sends nothing more and reports the rate limit with
the wait, not a timeout. Nothing here was verified against a live quota-limited provider — the numbers in the ADR
come from the log that started it, and the tests use wiremock and WireMock's `Retry-After` header.

#### Rate-limit patience for the index build (ADR 0041)

A second real run showed what ADR 0040 had left open: `gemini-embedding-2` asked for **46s**, the per-call budget is
30s, and the index build failed with that wait in the message. Re-running cannot help — `EmbeddingService` stores
nothing unless every batch succeeded, so it re-embeds the same chunks and meets the same wall.

```
cd backend && ./mvnw test -Dtest="RateLimitPatienceTest,SymbolChunkerTest"  → 13 tests pass
cd backend && ./mvnw test -Dtest=SuggestionIntegrationTest                 → 3 tests pass (28s)
cd backend && ./mvnw verify                                                → 255 tests pass, 0 failures, 1 skipped
```

`RateLimitPatience` (in `ai/gateway`, beside the retry policy it completes) wraps one step of a long-running
sequence: it catches a `RATE_LIMITED` failure, sleeps what the provider asked for, and calls again with a fresh
budget. It is bounded by `bcosmos.retrieval.rate-limit-patience` (`BCOSMOS_RETRIEVAL_RATE_LIMIT_PATIENCE`, default
`2m`) per build, by ten sittings at most, and by "only when the provider stated a wait" — a rate limit that says
nothing still goes straight back to the caller. `EmbeddingService` creates one per index build (the instance adds up
what that build has waited, so it must not be shared), and no other caller uses it: a single answer somebody is
waiting on still fails fast.

The end-to-end test is the one to look at:
`buildsTheIndexAnywayWhenTheProviderRateLimitsTheFirstBatches` refuses the first two index-build requests at the fake
provider (priorities put the scenario stubs above `stubEmbeddings`) and asserts the suggestion still comes back
`PROPOSED` with a 3-chunk index and exactly three index-build requests. It cannot pass by accident: `max-attempts=2`
means the third request can only come from the build sitting the window out. Each passthrough of the real 429 path
costs about three seconds of suite time (two one-second waits plus the fixture analysis).

## Traps and lessons from previous sessions (read these, they each cost real time)

1. **`analysis-core` is installed into the local Maven repository by hand.** With one Git repo per module there is no
   reactor, so `backend`, `analysis-worker` and `miner` resolve `com.bcosmos:analysis-core:0.0.1-SNAPSHOT` from
   `~/.m2`. After **every** change in `analysis-core`: `cd analysis-core && ./mvnw install`.
2. **A running backend locks that jar on Windows.** A developer-run backend (`-Dspring.profiles.active=dev`, often
   from IntelliJ) keeps `~/.m2/repository/com/bcosmos/analysis-core/0.0.1-SNAPSHOT/analysis-core-0.0.1-SNAPSHOT.jar`
   open, so `./mvnw install` fails with *"Failed to install artifact … .tmp → …jar"*. Do **not** kill the user's
   process. Work around it: `cd analysis-core && ./mvnw package -DskipTests && cp -f
   target/analysis-core-0.0.1-SNAPSHOT.jar ~/.m2/repository/com/bcosmos/analysis-core/0.0.1-SNAPSHOT/analysis-core-0.0.1-SNAPSHOT.jar`.
   Tell the user to restart the dev backend afterwards.
3. **Docker is not on the PATH in this shell.** The CLI lives at
   `/c/Users/amine/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe`; add that directory to `PATH` before
   any `./mvnw verify` that uses Testcontainers. `JAVA_HOME` is
   `/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot`.
4. **PMD's rule baseline is a trap.** `analysis-core/src/main/resources/pmd/bcosmos-baseline.properties` silently
   produces an **empty rule set** — no exception — when a reference names a rule PMD no longer has (`UnusedImports`
   was removed in PMD 7) and PMD logs only a *deprecation warning* for a rule that moved (`design.xml` →
   `errorprone.xml` for `AvoidCatchingGenericException`; PMD 8 removes the alias). Both defects were found and fixed
   in Phase 3: the loader now throws when a reference resolves to no rule, the baseline is
   `EmptyCatchBlock`, `UnusedPrivateField`, `UnusedPrivateMethod`, `errorprone/AvoidCatchingGenericException`, and
   `JavaAdapterAnalyzeTest.everyBaselineRuleFires` pins that each listed rule really fires. `UnusedLocalVariable` is
   deliberately **out** of the baseline: under the pinned PMD 7.28.0 it reports nothing when a scope has exactly one
   unused local. Any change to that file changes `rulesetHash`, i.e. the identity of every run — and
   `docs/rule-catalog.md` plus `backend/src/main/resources/rules/rule-descriptions.properties` must be updated with
   it (`RuleCatalogTest` fails if they drift).
5. **`@ConditionalOnMissingBean` on a `@Component` is unreliable** (registration order). Use an explicit
   `@ConditionalOnProperty(name = "bcosmos.kafka.enabled", havingValue = "false", matchIfMissing = true)` to pick the
   alternative bean, the way `InProcessJobDispatcher` and `DisabledEventPublisher` do.
6. **Mockito *is* on the backend test classpath** despite the granular `spring-boot-starter-*-test` starters (the
   build prints a Mockito self-attach warning). Prefer a small hand-written fake over hand-implementing a
   `JpaRepository` — that went wrong once already.
7. **Kafka container and topics:** the shared `TestcontainersConfiguration` already starts
   `apache/kafka-native:4.3.1` for the backend, so a `@SpringBootTest` with `bcosmos.kafka.enabled=true` is the
   cheapest way to test a real publish. Expect one `UNKNOWN_TOPIC_OR_PARTITION` metadata warning on the first send
   while topic creation races; the message still arrives.
8. **`docs/PHASES.md` is a short stub** pointing at `docs/BCOSMOS_PHASES.md`; the spec calls the file
   `docs/PHASES.md`. Do not let the two drift further.
9. **Angular OnPush + HTTP errors in tests:** when testing error states in `OnPush` components, use
   `fixture.flush({message:'...'}, {status: 500, statusText: 'Server Error'})` rather than
   `.error(new ErrorEvent(...))` — the `ErrorEvent` path does not trigger change detection in zone-less vitest.
10. **Angular component style budgets:** the `anyComponentStyle` budget in `angular.json` was raised to
    `maximumWarning: 8kB / maximumError: 16kB` from the original 4/8 kB because `corpus.scss`,
    `analytics.scss`, and `benchmarks.scss` all exceed 4 kB. Do not revert this.
11. **A native PostgreSQL on the host steals `localhost:5432`.** This machine also runs the Windows service
    `postgresql-x64-18`, which binds `0.0.0.0:5432` at the same time as Docker's proxy. `docker compose ps` says
    the container is healthy, but the backend dies during Liquibase with
    `FATAL: password authentication failed for user "bcosmos"`, because the connection lands on the *host* server.
    The compose file publishes a second port for this, and **the dev profile now defaults to it**
    (`application-dev.yml` → `${BCOSMOS_DB_PORT:5433}`, which compose publishes on every machine, so 5433 is the
    default for the worker too). Set `BCOSMOS_DB_PORT=5432` only when you really mean the host server. Do not stop
    or reconfigure the user's PostgreSQL service to work around it, and do not "fix" it by changing the credentials.
12. **Docker Desktop may be stopped.** Start it with
    `"/c/Users/amine/AppData/Local/Programs/DockerDesktop/Docker Desktop.exe"` and poll
    `docker version --format '{{.Server.Version}}'` (it needs roughly a minute after login); `./mvnw verify` in
    `backend`, `analysis-worker` and `miner` fails without it, and `docker compose` cannot start Postgres at all.
13. **A missing parser binding looks like a broken bean, not a broken classpath.** `Factory method
    'typeScriptAdapter' threw exception … org/treesitter/TSLanguage` means the **launcher's** classpath lacks
    `io.github.bonede:tree-sitter`. The JVM verifies `TypeScriptAdapter` while the bean is created, so the failure
    lands on the bean and takes `PatchValidator`, `SuggestionService` and `ModelCompareService` down with it — no
    analysis has to be requested. Maven is not the culprit: `./mvnw -q dependency:build-classpath
    -Dmdep.outputFile=/tmp/cp.txt` in `backend` lists `tree-sitter-0.25.3.jar`, the fat jar carries it under
    `BOOT-INF/lib/`, and both `backend` and `analysis-worker` declare it directly now ([ADR
    0036](decisions/ADR-0036-parser-binding-as-direct-dependency.md)). When a user reports it, ask how they launch
    the app: an IDE that assembled its library list before Phase 10 keeps it until the Maven project is reimported,
    and a rebuild is part of the answer. `cd backend && ./mvnw test -Dtest=AnalysisConfigurationTypeScriptBindingTest`
    is the five-second control (it boots `AnalysisConfiguration` and parses one TypeScript file).
14. **Port collisions between compose and the apps.** The `spark` profile used to publish the master UI on host 8080
    and the worker UI on 8081 — the backend's and the analysis-worker's ports. It no longer does: the UIs are on
    **7080** and **7081** ([ADR 0017 amendment](decisions/ADR-0017-spark-in-docker.md)), and the RPC port is still
    7077. If a `Web server failed to start. Port 8080 was already in use` shows up anyway, the container predates
    that change — `docker compose --profile spark up -d --force-recreate spark-master spark-worker` — or the user has
    a second backend running, which the Angular proxy at `localhost:8080` makes worth asking about before you move
    this one to another port.

## Definition of done for the whole project

All ten phases implemented and verified per `docs/BCOSMOS_PHASES.md`, plus:

- `docs/benchmarks.md` with measured pipeline numbers and a named dataset;
- a dataset card with real row counts, rules covered, licences and filters (Phase 5) ✅;
- a leaderboard with sample sizes and stated limits (Phase 6) ✅;
- an honest fine-tuning write-up, including if it did not beat the baseline (Phase 7);
- a `README.md` demo script a new user can reproduce (Phase 8);
- `docs/BCOSMOS_SPEC_v2.md` "Current status" updated after every phase, `AGENTS.md` "Current phase" kept current,
  and this handoff file updated or deleted when it is no longer true.
