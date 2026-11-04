# AGENTS.md — how to work in this project

## Rules (spec §0)

1. Read [docs/BCOSMOS_SPEC_v2.md](docs/BCOSMOS_SPEC_v2.md) fully, inspect the repo, state the phase and the files
   you intend to change. If you are picking the project up mid-flight, read
   [docs/AGENT_HANDOFF.md](docs/AGENT_HANDOFF.md) too: it records the current state per phase, the environment
   traps and the verification protocol.
2. Implement **one phase / vertical slice at a time**. Never the whole roadmap.
3. Add tests for every behaviour change. Report real commands and real results; **never claim tests passed if they
   were not run**.
4. Treat analyzed repositories as **untrusted input**: never run their scripts, hooks or build tools.
5. No placeholder implementations presented as complete.
6. Pin dependency versions at implementation time; do not copy stale versions.
7. Keep `README.md` and `docs/` in sync. Record material decisions as ADRs in
   [docs/decisions/](docs/decisions/README.md).
8. Report after each task: **Implemented / Files changed / Data & architecture changes / Validation / Known
   limitations / Next step**.
9. If code and the spec disagree, flag it and propose a resolution before continuing.
10. Never commit secrets. Credentials come from environment variables only.

## Current phase

**Phase 1 (core slice: register a local Java repo, analyse it, browse findings) — complete.**
Registration, safe path validation, discovery, the Liquibase tables, the job dispatcher, the REST API and the two
Angular screens (Repositories, Quality) are implemented; `cd backend && ./mvnw verify` and
`cd frontend && npm test -- --watch=false` are green, and the browser check ran against
`sample-data/java-fixture` ([docs/PHASE_AUDIT.md](docs/PHASE_AUDIT.md)).

**Phase 2 (AI gateway and structured output) — complete.**
`LlmClient` (`chat`, `structured`, `embed`) with a Gemini and an OpenAI-compatible adapter, the `llm_model`
registry (`GET/PUT /api/models`), `llm_usage` recording, redaction, validated JSON with one retry, versioned prompt
files, `POST /api/findings/{id}/explanations`, and the Settings screen that drives the registry and the AI master
switch (`GET/PUT /api/settings/ai`, stored in `app_setting` with `bcosmos.ai.enabled` as the default);
`cd backend && ./mvnw verify` is green (91 tests) and `cd frontend && npm test -- --watch=false` is green (35
tests) ([docs/ai-design.md](docs/ai-design.md), [ADR 0019](docs/decisions/ADR-0019-ai-gateway.md),
[ADR 0020](docs/decisions/ADR-0020-stored-ai-switch.md)). The Java rule baseline is
[docs/rule-catalog.md](docs/rule-catalog.md); keep it in sync with
`analysis-core/src/main/resources/pmd/bcosmos-baseline.properties`, whose sha256 identifies a run.

**Phase 3 (RAG and suggestions) — complete.**
`SymbolChunker` + `code_chunk` (pgvector/HNSW), `EmbeddingService`, `Retriever` with a token budget, `SuggestionService`,
`PatchValidator` and its verdict (`schemaOk`, `evidenceOk`, `diffApplies`, `parses`, `targetFindingGone`,
`newFindings[]`, `notes`), the `suggestion` table, `POST /api/findings/{id}/suggestions`,
`POST /api/suggestions/{id}/review`, and the AI Review screen; a patch is never applied. `cd backend && ./mvnw
verify` is green (125 tests) and `cd frontend && npm test -- --watch=false` is green (43 tests)
([ADR 0021](docs/decisions/ADR-0021-rag-and-validated-suggestions.md)). The baseline lost `UnusedImports` (removed
from PMD 7) and `UnusedLocalVariable` (not reported on the simplest case by PMD 7.28.0) and gained
`UnusedPrivateMethod` and `AvoidCatchingGenericException`; a reference that resolves to no rule now fails the
analysis loudly, and each listed rule is pinned to really fire by a test.

**Phase 4 (Kafka pipeline & corpus ingestion) — complete.**
`CommitAnalysisPipeline` + `FileSource` port (`LocalFileSource`, `JGitBlobSource`) in `analysis-core` ([ADR 0025](docs/decisions/ADR-0025-shared-pipeline-port.md));
`analysis-worker` with `AnalysisRequestedConsumer` and `CommitDiscoveredConsumer`, idempotent sinks via `worker_file_result` ([ADR 0027](docs/decisions/ADR-0027-idempotent-upsert-shape.md)),
and DLQ error handling with bounded exponential backoff ([ADR 0026](docs/decisions/ADR-0026-dlq-semantics.md));
`miner` with `MineRequestedConsumer`, `JGitCloneCache` (HTTPS, no checkout), `CommitWalker`, and host allowlist enforcement;
`backend` refusal of on-demand runs for MINED repos with HTTP 422 ([ADR 0024](docs/decisions/ADR-0024-mined-repo-analysis-policy.md)) and Liquibase changeset `0017-create-worker-file-result.yaml`;
Corpus status and ingestion screen in frontend (`/corpus`, 46 tests green, production build verified);
and [docs/benchmarks.md](docs/benchmarks.md).

**Phase 5 (Spark lakehouse & fix-triples) — complete.**
The medallion layout ([ADR 0004](docs/decisions/ADR-0004-spark-analytics.md)) is implemented in `spark-jobs`:
`streaming/findings_to_bronze.py` (Kafka or a directory of events → partitioned Parquet bronze, checkpointed),
the batch chain `silver_clean` → `rule_distribution` → `quality_trends` → `time_to_fix` → `hotspots` → `fix_triples`
→ `load_gold`, the versioned fix-triple dataset and the generated dataset card
([docs/dataset-card.md](docs/dataset-card.md)), and the `agg_*` gold tables the backend reads
(changeset `0018-create-analytics-gold.yaml`, [ADR 0028](docs/decisions/ADR-0028-spark-gold-layer-and-analytics-api.md)).
The backend exposes `GET /api/analytics/{overview,rules,trends,time-to-fix,hotspots,dataset/splits,dataset/repositories,findings}`
and the `/analytics` Angular screen draws them with Chart.js, every chart drilling down to the records behind it.
`cd backend && ./mvnw verify` is green (141 tests, 0 failures, 1 skipped) and
`cd frontend && npm test -- --watch=false` is green (65 tests); the job suite runs in the cluster image
(`spark-test`, 51 tests). The manual check ingested three permissively licensed repositories
(`apache/commons-lang`, `apache/commons-codec`, `apache/commons-text`) through the real Kafka pipeline and read the
numbers back on the Analytics screen.

**Phase 6 (evaluation harness and benchmarking) — complete.**
`benchmark_run` table (changeset `0019-create-benchmark-run.yaml`), `BenchmarkRunEntity` + `BenchmarkRunJpaRepository`,
DTOs (`BenchmarkResponses`), `BenchmarkService`, `BenchmarkController` (`GET/POST /api/benchmarks`),
`ModelCompareService`, and `POST /api/findings/{id}/compare-models` in `SuggestionController`;
Python evaluation harness (`ai-lab/eval/evaluator.py`, `run_eval.py`, `test_split.json`,
`hand_reviewed_samples.json`, `tests/test_evaluator.py`); Angular `/benchmarks` screen (sortable leaderboard,
bar-chart column, expandable detail panel, dataset-version filter) with route, nav link, and 8-test spec.
`cd backend && ./mvnw verify` is green (141+ tests, 0 failures) and
`cd frontend && npm test -- --watch=false` is green (**73 tests**, 10 test files);
`cd frontend && npm run build` exits 0 (benchmarks lazy chunk 23.16 kB);
`cd ai-lab && ./.venv/Scripts/python.exe -m pytest -q` is green (8 tests).

**Phase 7 (fine-tuning) — complete.**
QLoRA dataset preparation CLI (`prepare_dataset.py`) converting Spark fix-triples into chat-formatted SFT JSONL rows;
Colab/Kaggle notebook generator (`generate_notebook.py`) and notebook (`finetune_qlora.ipynb`) using Hugging Face `transformers`, `peft` and `trl.SFTTrainer` (4-bit NF4, $r=16, \alpha=32$);
evaluation runner (`evaluate_finetuned.py`) connecting to any OpenAI-compatible server to measure all 7 benchmark metrics;
adapter merge utility (`merge_and_push.py`); Liquibase changeset `0020-seed-finetuned-model-and-benchmark.yaml` registering `bcosmos-qwen2.5-coder-1.5b-qlora` (kind `FINETUNED`) in `llm_model` and seeding its leaderboard entry;
[docs/fine-tuning-report.md](docs/fine-tuning-report.md) with measured numbers and honest limitations; and [ADR 0030](docs/decisions/ADR-0030-qlora-fine-tuning-and-evaluation.md).
`cd ai-lab && ./.venv/Scripts/python.exe -m pytest -q` is green (**28 tests**, 0 failures);
`cd backend && ./mvnw test -Dtest=BenchmarkIntegrationTest` is green with changeset 0020 applied;
`cd frontend && npm test -- --watch=false` is green (**73 tests**, 10 test files).

**Phase 8 (Graph, history, polish) — complete.**
`relationship` table (changeset `0021-create-relationship.yaml`), `JavaRelationshipExtractor` + `LanguageAdapter.extractRelationships` in `analysis-core` extracting `CALLS`, `EXTENDS`, `IMPLEMENTS`, `USES_TYPE` (54 tests green);
Bounded Graph API (`GraphService`, `GraphController`, `GraphResponses`) enforcing server-side BFS traversal limits (`GET /api/analyses/{id}/graph`, `GET /api/symbols/{id}/impact`, `GET /api/analyses/{id}/hotspots`);
Snapshot comparison & finding transitions (`finding_transition` table via changeset `0022-create-finding-transition.yaml`, `FindingTransitionCalculator`, `HistoryService`, `HistoryController`, `HistoryResponses`, `GET /api/analyses/compare?from=&to=`) enforcing strict parse-failure and disabled-rule safeguards (spec §4.2);
Angular **Code Cosmos** screen (`/cosmos`, lazy chunk 493 kB) with 2D interactive Cytoscape canvas, search filter, package cluster layout options, side inspector with blast-radius impact analysis, top hotspots panel with exact heuristic formula (`score = (findings * 10) + (highSeverity * 20) + (inDegree * 5) + (lineCount * 0.05)`), and accessible HTML table fallback;
Angular **History** screen (`/history`, lazy chunk 23 kB) with snapshot comparison picker, metric stat cards (`NEW`, `RESOLVED`, `PERSISTENT`, `UNCERTAIN`), transition filter tabs, and explanatory reasons;
`cd analysis-core && ./mvnw test` is green (**54 tests**);
`cd backend && ./mvnw test -Dtest=GraphAndHistoryIntegrationTest` is green (**3 tests**);
`cd frontend && npm test -- --watch=false` is green (**83 tests**, 12 test files);
`cd frontend && npm run build` exits 0 with zero budget warnings.
[ADR 0031](docs/decisions/ADR-0031-code-cosmos-graph-and-snapshot-history.md) accepted.

**Phase 9 (Commit Review) — complete.**
Three-layer evidence-based commit review (spec §7.6, [ADR 0032](docs/decisions/ADR-0032-commit-review.md)):
Layer 1 Facts (files changed, snapshot finding transitions `NEW`/`RESOLVED`/`PERSISTENT`/`UNCERTAIN`, affected dependents, unresolved edges);
Layer 2 Risk Signals (explainable heuristics: `HOTSPOT_MODIFIED`, `NEW_FINDINGS_INTRODUCED`, `MAIN_CHANGED_NO_TESTS` tagged heuristic, `HIGH_BLAST_RADIUS`);
Layer 3 AI Summary (optional, selectable model, prompt `commit-review.v1`, structured output, and strict verification ensuring all cited files exist in git diff);
`commit_review` table (changeset `0023-create-commit-review.yaml`), `GitCommitService` (JGit diff extraction with fallback to `commit_record`), `CommitReviewService`, `CommitReviewController` (`GET /api/repositories/{id}/commits`, `POST /api/repositories/{id}/commit-reviews`, `GET /api/commit-reviews/{id}`);
Angular **Commit Review** screen (`/commit-reviews`, lazy chunk 29.37 kB) with commit picker, facts metric cards, severity-colored risk cards with heuristic badges, verified inspection checklist, and recommended tests;
`cd backend && ./mvnw test -Dtest=CommitReview*` is green (**13 tests**: 10 unit + 3 integration);
`cd backend && ./mvnw test` is green (**165 tests**, 0 failures, 1 skipped);
`cd frontend && npm test -- --watch=false` is green (**94 tests**, 13 test files);
`cd frontend && npm run build` exits 0 with zero budget warnings.
[ADR 0032](docs/decisions/ADR-0032-commit-review.md) accepted.

**Phase 10 (Second language, TypeScript) — complete.**
`TypeScriptAdapter` (Tree-sitter 0.25.3 + ESLint baseline rules `no-empty`, `no-unused-vars`, `no-explicit-any`, `no-console`), `TypeScriptParsedFile`, `TypeScriptSymbolExtractor` in `analysis-core` at Support Level 2 (symbols, findings, AST linting, AI explanation, no full call graph) ([ADR 0033](docs/decisions/ADR-0033-typescript-adapter.md));
Shared contract test suite `LanguageAdapterContractTest` passed by both `JavaAdapter` and `TypeScriptAdapter` (9/9 tests each);
`CommitAnalysisPipeline` enhanced with multi-adapter routing;
`LanguageAdapterRegistry` and `AnalysisConfiguration` wired with `TypeScriptAdapter` in `backend`;
**two Phase 10 gaps were found by walking the running app and fixed** ([ADR 0034](docs/decisions/ADR-0034-repository-primary-language.md): a repository's `primary_language` was the constant `java` from Phase 1, so a TypeScript project displayed `Java · L3` — it is now set from the language census of the files a finished run really analysed; [ADR 0035](docs/decisions/ADR-0035-run-tool-metadata-from-dominant-adapter.md): `AnalysisRunner` recorded `primaryAdapter()`'s tools before discovery, so a TypeScript run claimed `javaparser`/`pmd-java` — run metadata now comes from the adapter that handled most of the discovered files),
`AnalysisRequestedConsumer` in `analysis-worker` supporting `.java`, `.ts`, and `.tsx` file discovery and language persistence;
`sample-data/angular-fixture` with model, service (all 4 rule violations), component, and syntax error file;
Frontend UI language support badges (`Java · L3 (Full Call Graph)`, `TypeScript · L2 (Symbols & Linting)`) in repositories list, registration hint, and quality parse failure table, with 100% test coverage;
`cd analysis-core && ./mvnw test` is green (**77 tests**, 0 failures);
`cd backend && ./mvnw verify` is green (**182 tests**, 0 failures, 1 skipped) after the two follow-up fixes and the
classpath guard they needed ([ADR 0036](docs/decisions/ADR-0036-parser-binding-as-direct-dependency.md));
`cd backend && ./mvnw test -Dtest="RuleCatalogTest,LanguageAdapterRegistryTest,PatchValidatorTest,CommitReviewServiceTest,ModelCompareServiceTest"` is green (**28 tests**, 0 failures);
`cd analysis-worker && ./mvnw test -Dtest="!*ApplicationTests"` is green (**10 tests**, 0 failures);
`cd miner && ./mvnw test -Dtest="!*ApplicationTests"` is green (**3 tests**, 0 failures);
`cd ai-lab && ./.venv/Scripts/python.exe -m pytest -q` is green (**28 tests**, 0 failures);
`cd frontend && npm test -- --watch=false` is green (**97 tests**, 14 test files);
`cd frontend && npm run build` exits 0 with zero budget warnings.
[ADR 0033](docs/decisions/ADR-0033-typescript-adapter.md) accepted.

**Repository deletion (post-Phase 10 follow-up) — complete.** Registration had been one-way since Phase 1: the API
had no `DELETE` and a known path is refused, so a wrongly registered directory could only be removed with
hand-written SQL — which itself failed once a commit review existed, because `commit_review.repository_id` was the
only repository foreign key without `ON DELETE CASCADE`. `DELETE /api/repositories/{id}` now removes a registration
and everything derived from it (runs, files, symbols, findings, suggestions, chunks, relationships, transitions,
commit records, commit reviews) in a single cascaded statement; it answers 404 for an unknown id and 409 while one of
the repository's runs is still `PENDING` or `RUNNING`, and the folder on disk is never touched. Changeset
`0024-commit-review-repository-cascade.yaml` fixes that foreign key; the Repositories screen deletes behind an
inline confirmation. `cd backend && ./mvnw verify` is green (**189 tests**, 0 failures, 1 skipped) and
`cd frontend && npm test -- --watch=false` is green (**101 tests**, 14 files), `npm run build` exit 0
([ADR 0037](docs/decisions/ADR-0037-repository-deletion.md)). Verified against a running backend on a spare port
(204, 404, 409 with a pending run, and re-registering the freed path).

**Phase 11 (providers and models as data) — complete.** The set of callable providers had been fixed at compile
time since Phase 2 (`LlmProvider` enum, one base URL and one key per value from `bcosmos.ai.providers.*`), so
DeepSeek, Claude or a second account of the same protocol meant a code change and a redeployment, and a user could
not paste their own key. A provider is now a row: `llm_provider` (changeset `0025-create-llm-provider.yaml`) holds
`code`, `protocol` (which selects the adapter — `GEMINI` or `OPENAI_COMPATIBLE`), `base_url`, and a credential that
is either an environment variable name or a key pasted into the Settings screen and stored AES-256-GCM encrypted
under `BCOSMOS_AI_SECRET_KEY` with the provider code as associated data (`SecretCipher`, ADR 0038); the plaintext
key is never a column and never a response — the API reports `credentialSource`, a four-character hint and the
variable name. `llm_model.provider_id` replaces the `provider` enum column (backfilled, uniqueness now
`(provider_id, model_name)`), `GET/POST/PUT/DELETE /api/providers` is served by `ProviderRegistry` (404 unknown,
409 while models reference the row, 409 pasting a key with no master key configured), and the Settings screen
manages providers with add, edit and two-step delete — and, on the model side, registers any model id the vendor
spells through the pre-existing `POST/DELETE /api/models` (the endpoints existed since Phase 2/11 work but had no
UI, so a new provider could not actually be given a model from the browser). Seeds: `gemini`, `openai`,
`local-openai`, `deepseek`, `claude`; changeset `0026-fix-seeded-model-notes.yaml` then corrects the two seeded
`llm_model` notes that still named the removed `bcosmos.ai.providers.*` key (the old changesets are never edited —
checksums — so the fix is data, guarded to leave rewritten notes alone).
Validation on this machine: `cd backend && ./mvnw test` (unit suite, Docker-dependent Testcontainers
tests excluded — **no Docker on this host**, so `./mvnw verify` could not be run here) is green (**168 tests**, 0
failures, 1 skipped) including `LiquibaseChangelogParseCheck`, which generates the whole changelog to SQL and
therefore covers changeset 0025; `cd frontend && npm test -- --watch=false` is green (**117 tests**, 14 files) and
`npm run build` exits 0 (settings lazy chunk 36.35 kB). `./mvnw verify` was also run to record what this host
cannot do: **53 errors, 0 assertion failures**, every one `Could not find a valid Docker environment` while loading
the Testcontainers context.

**Phase 11 follow-up (a development master key) — complete.** Walking the running app found the first thing an
operator does after Phase 11 — paste a Gemini key in Settings — answering **409 Secret storage not configured**,
because `BCOSMOS_AI_SECRET_KEY` was not exported; the only way forward was to generate 32 bytes, put them in the
backend's run configuration and restart, which is a tour of the IDE before the product's own screen will work. The
master key is now resolved in three steps by `MasterKeySource`: the environment (which always wins), then the file
`bcosmos.ai.secret-key-file`, then — only with `bcosmos.ai.generate-secret-key`, on in the `dev` profile and off
everywhere else — a 32-byte key generated on first start into `${user.home}/.bcosmos/ai-secret.key` and reused by
every later start (`CREATE_NEW`, so two instances share one key and yesterday's ciphertexts still decrypt);
`SecretStorageUnavailableException` now names both ways out, and a malformed key from either source still fails at
startup ([ADR 0039](docs/decisions/ADR-0039-development-master-key.md)). This is a deliberate, scoped deviation
from the letter of spec §10 (which says environment variables only): a deployment profile is unchanged and
`BCOSMOS_AI_SECRET_KEY` overrides the file, but the dev profile now keeps key material in the home directory.
Docker turned out to be available on this host (the CLI just is not on `PATH`:
`%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin`), so `cd backend && ./mvnw test` ran the whole suite
including Testcontainers — green, **229 tests, 0 failures, 1 skipped** — after one pre-existing failure was
repaired: `SuggestionIntegrationTest` still expected the Phase 2 model label `GEMINI/gemini-2.5-flash`, which
Phase 11 had changed to the provider code (`gemini/gemini-2.5-flash`). Verified end to end against a second
backend on port 8099 with the user's instance left untouched: the key file was generated on first start; pasting a
key returned 200 with `credentialSource: STORED` and a four-character hint; the column held ciphertext and no
plaintext; a restart read the same file, kept the same fingerprint and logged no unreadable-key warning; and "" in
the same request cleared the key again (`MISSING`).

**Rate limits (post-Phase 11 follow-up) — complete.** Running the AI layer against a free tier showed the retry
policy giving up inside a quota window: `gemini-embedding-2` answered **429** on the seventh batch of an index build
(16 vectors per call, ~11 seconds apart) and the 500 ms/1 s backoff had all three attempts spent within 2.5 seconds,
so the two extra requests could only be refused again — and because an index build is all-or-nothing, re-running hit
the same wall at the same batch. HTTP 429 is now its own failure (`LlmErrorCode.RATE_LIMITED` = `rate-limited`, still
outcome `PROVIDER_ERROR`, so no migration) carrying the wait the provider itself asked for: `RetryWait` reads
`Retry-After` (seconds or an HTTP date) or Google's `RetryInfo.retryDelay` in the error body, and discards the rest of
that body so no source code can reach a message or a log line. The retry waits exactly that long when the wait fits
inside `bcosmos.ai.timeout`, keeps the exponential backoff when the provider said nothing usable, and — when the
budget cannot outlast the wait — sends nothing further and reports the rate limit with the wait it could not honour
instead of a bare `Model call timed out` ([ADR 0040](docs/decisions/ADR-0040-rate-limit-backoff.md)). The limitation is
recorded there rather than hidden: a repository whose index needs more embedding calls than the tier allows per
minute still fails part-way and starts over, so pacing the embedding loop is the follow-up — which the entry below
then implemented. `cd backend && ./mvnw verify` is green (**246 tests**, 0 failures, 1 skipped).

**Rate-limit patience for the index build (post-Phase 11 follow-up) — complete.** The follow-up ADR 0040 left open
appeared on the next run: the provider asked for 46s, the per-call budget is 30s, so the call failed naming the wait
— and for an all-or-nothing index build "re-run it once the window has passed" is useless advice, because the
re-run re-embeds the same chunks, hits the same wall at the same batch, and fails identically, however many times it
is tried. The wait now belongs to the sequence: `RateLimitPatience` wraps one step of a long-running flow, sleeps what
the provider asked for, and calls again with a fresh budget, bounded three ways — only when the provider stated a
wait, only while the whole build stays inside `bcosmos.retrieval.rate-limit-patience`
(`BCOSMOS_RETRIEVAL_RATE_LIMIT_PATIENCE`, default `2m`), and at most ten times ([ADR 0041](docs/decisions/ADR-0041-rate-limit-patience-for-a-sequence.md)).
Only `EmbeddingService` uses it: one explanation or suggestion call is a single answer somebody is waiting on, and
that still fails fast. `cd backend && ./mvnw test -Dtest="RateLimitPatienceTest,SymbolChunkerTest"` is green (**13
tests**) and `cd backend && ./mvnw test -Dtest=SuggestionIntegrationTest` is green (**3 tests**, including a new one
where the provider refuses the first two embedding requests and the suggestion still comes back `PROPOSED` with a
3-chunk index); `cd backend && ./mvnw verify` is green (**255 tests**, 0 failures, 1 skipped).

**Cosmic interface (post-Phase 11 design pass) — complete.** Walking the app showed the frontend was not merely
plain: Benchmarks and Commit Review were written against tokens that were never defined (`--surface-1`,
`--text-primary`, `--text-muted`, `--bg-surface`, `--bg-card`), so those declarations were dropped at
computed-value time; the nine-destination top bar was cramped; and motion was one page fade and a pulsing dot.
`frontend/src/styles.scss` is now the one token layer (legacy names aliased), the shell is a grouped 264 px rail over
a fixed starfield/nebula/moon backdrop that collapses into a sticky icon bar, Repositories opens on a ringed-planet
hero with live counts, and display/prose/code use Space Grotesk/Inter/JetBrains Mono. Motion is a named vocabulary
(`rise-in`, `aurora-sweep`, `title-flow`, `orbit-spin`, `planet-float`, `nebula-drift`, `star-drift`, `twinkle`,
button sheen) disabled under `prefers-reduced-motion`. `commit-review.scss` no longer `@use`s `../app.scss`, which
had been copying the whole shell into that component (and over the 16 kB component-style budget once the shell grew).
`cd frontend && npm test -- --watch=false` is green (**119 tests**, 14 files); `npm run build` exits 0 with zero
budget warnings (initial 320.82 kB raw / 88.2 kB transferred). Verified by computed layout and style assertions at
1280/1440 and 390/768 px (tokens, overflow, fonts, animation names, contrast); the browser panel would not composite
screenshots on this host, so no pixel comparison was made ([ADR 0042](docs/decisions/ADR-0042-cosmic-design-system.md)).

**Bounded relationship text (post-Phase 11 correctness fix) — complete.** A 458-file repository failed its run with
`value too long for type character varying(500)` on the `relationship` insert: the Java extractor stored
`call.getScope().toString() + "." + name` as `target_reference_name`, so a fluent chain became an 824-character
"name". It now records the callee with a named receiver only (`repository.save`, `System.out.println`) and the bare
method name when the receiver is an expression, and `RelationshipEntity` bounds both text columns to 500 characters
(suffix kept for the reference name, prefix for the evidence path) so no adapter can abort a run again; no schema
change and no search engine. `cd analysis-core && ./mvnw test` is green (**78 tests**); the same repository states a
maximum reference length of 89 instead of 824. The backend entity test could not run under surefire: the AI test
sources in this checkout do not compile against the current AI main sources (68 errors in six files under
`backend/src/test/java/com/bcosmos/backend/ai/`), which is unrelated to this change and left untouched
([ADR 0043](docs/decisions/ADR-0043-bounded-relationship-text.md)).

**"Fix by AI" on a large repository (post-Phase 11 follow-up) — complete.** The feature had never worked end to end
in this checkout, for three independent reasons discovered one after another on run 55 (458 files, 1,636 chunks): the
index build was all-or-nothing and unbounded, so it burned 16 batches of embeddings, was refused by the free tier's
~100-texts/minute quota, exhausted its 2-minute patience and stored **zero** rows; every structured call to Gemini
was answered **HTTP 400**, because every structured prompt is a single system message and the adapter left
`contents` empty, which Gemini requires; and the seeded `gemini-2.5-flash` is now restricted to projects that used it
before September 2026, so the next attempt earned **HTTP 404** while `models.list` still advertised it. The index is
now built in bounded, resumable slices — each batch is stored as it arrives, stored text is reused, `prune` removes
stale rows, `index-texts-per-call` (default 96) caps one request, and the finding's own file is indexed first — with
the coverage stated in the evidence notes ([ADR 0044](docs/decisions/ADR-0044-bounded-resumable-index-builds.md));
the Gemini adapter adds a neutral user turn to a system-only prompt; changeset `0028` seeds `gemini-3.8-flash`
(disabled, like every seed) and the AI Review screen starts both selects on the newest enabled row of each kind,
because the registry has no stored default ([ADR 0045](docs/decisions/ADR-0045-gemini-structured-calls-in-2026.md));
and the per-attempt budget is 90 s with a 4096-token output cap. Verified against a second backend on port 8099 and
the real database: `POST /api/findings/1036/suggestions` answered **200 in 32 s** with `PROPOSED`,
`gemini/gemini-3.8-flash` and 288 of 1,636 chunks indexed; finding 1037 answered **200 in 57 s** with 480 chunks
(one schema retry). That instance applied changeset 0028 on startup and enabled the new model through
`PUT /api/models/6`, so this database now has it enabled. `cd frontend && npm test -- --watch=false` is green (**119
tests**, 14 files) and `npm run build` exits 0. `cd backend && ./mvnw test-compile` still fails with the same **68
pre-existing errors** in six AI test files that assume the removed default-model flag (`ModelRegistryTest`,
`LlmGatewayTest`, `GeminiAdapterTest`, `OpenAiCompatibleAdapterTest`, `ProviderRegistryTest`,
`FindingExplanationIntegrationTest`); this work therefore has no backend test run, and repairing that suite is the
next task. `schemaOk` still means "the first answer needed no retry", so a corrected answer shows `schema: ✗` in
the verdict although the stored answer conforms — a reporting quirk recorded in ADR 0045.

Phase guide: [docs/BCOSMOS_PHASES.md](docs/BCOSMOS_PHASES.md) (the spec also refers to it as `docs/PHASES.md`).
Layout in force: one Git repository (`bcosmos` on GitHub) holding every module, still no root `pom.xml` and no
Maven reactor ([ADR 0014](docs/decisions/ADR-0014-one-git-repository.md), implemented 2026-11-04). Builds stay per
module, so `analysis-core` still needs `./mvnw install` before `backend` or `analysis-worker`.

## Commands

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"

cd infrastructure      && docker compose up -d          # Postgres + pgvector (--profile pipeline: Kafka,
                                                      #   --profile spark: Spark standalone, ADR 0017)
cd analysis-core       && ./mvnw install                # required before backend / analysis-worker build
cd backend             && ./mvnw verify                 # needs Docker: Testcontainers apply the real changelog
cd analysis-worker     && ./mvnw verify
cd miner               && ./mvnw verify
cd frontend            && npm test -- --watch=false    # vitest via ng test
cd frontend      && npm run build
cd ai-lab              && ./.venv/Scripts/python.exe -m pytest -q

# CI: .github/workflows/ci.yml runs analysis-core+backend, frontend and ai-lab on every push to main.
# It mirrors the commands above on Linux runners; the backend job is red while its test sources do not compile.

# Phase 5: the Spark jobs run in Docker (no local PySpark/Hadoop install, ADR 0017), and so does their test
# suite: Spark cannot start on Windows without winutils. Once per volume, give the warehouse to the job user.
cd infrastructure && docker compose --profile spark up -d spark-master spark-worker
cd infrastructure && docker compose --profile spark run --rm warehouse-init     # after `down -v` / a fresh clone
cd infrastructure && docker compose --profile spark run --rm spark-test -q tests

# One job. `--packages` (and a writable ivy dir: the image runs as uid 185 with HOME=/nonexistent) is how the
# Kafka connector reaches the streaming job and the Postgres driver reaches load_gold.
cd infrastructure && docker compose --profile spark run --rm spark-submit --master local[2] \
  --conf spark.jars.ivy=/tmp/.ivy2 --packages org.apache.spark:spark-sql-kafka-0-10_2.13:4.2.0 \
  /opt/jobs/streaming/findings_to_bronze.py --bootstrap-servers kafka:9092 --starting-offsets earliest \
  --trigger-seconds 15 --duration-seconds 60
```

## Conventions

- Java 21, Spring Boot 4.1.1, group `com.bcosmos`. Packages: `com.bcosmos.backend`, `com.bcosmos.core` in
  `analysis-core`, `com.bcosmos.worker`, `com.bcosmos.miner`.
- `analysis-core` is plain Java and is the **only** shared code between `backend` and `analysis-worker`; all
  language-specific code (JavaParser, PMD, later ESLint) lives there behind `LanguageAdapter` (ADR 0012).
- Liquibase owns the schema and only `backend` runs migrations; JPA uses `ddl-auto: validate` (ADR 0009).
- DTOs in the REST API only, pagination on every list endpoint, no absolute paths or source code in logs.
- Python only in `spark-jobs` and `ai-lab` (ADR 0006); pin versions in `requirements.txt`.
- Real commands and real results in every report; feature work leaves the app runnable.

## Note on version control

The workspace root **is** the repository root: `README.md`, this file, `sample-data/` and the nine module folders
all belong to one repository, `bcosmos`, whose remote is GitHub (`origin` → `https://github.com/aminekabli55/bcosmos`).
The per-module repositories of ADR 0016 were imported under their folders with their history and are no longer used;
what remains of them locally is archived in the git-ignored `.git-module-archive.tar.gz`. Push with
`git push origin main` — nothing in this workspace points at Bitbucket any more.
