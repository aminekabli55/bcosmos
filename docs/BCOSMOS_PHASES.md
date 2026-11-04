# BCosmos: Phase guide for the AI agent

Companion to `docs/BCOSMOS_SPEC.md`. The spec says **what** BCosmos is. This file says **what to build in each phase**, in order.
Put this file in `docs/PHASES.md`.

## How to use

1. Work on **one phase at a time**, in a new agent session and a new Git branch (`phase-1`, `phase-2`, ...).
2. Paste the prompt template below and replace `{N}` and `{name}`.
3. When the agent finishes: run the **Manual check**, read the diff, then commit and merge.
4. Update the "Current status" section in the spec. Only then start the next phase.

## Prompt template (paste this)

```
Read AGENTS.md, docs/BCOSMOS_SPEC.md and docs/PHASES.md.
Implement Phase {N} ("{name}") only. Do not start other phases.
First write a short plan: the files you will create or change.
Then work in small steps. After each step run the build and the tests.
At the end use the report format from section 0 of the spec
(Implemented / Files changed / Data & architecture changes / Validation / Known limitations / Next step).
Rules: never claim a test passed if you did not run it. Never commit secrets.
If the spec and the code disagree, stop and tell me before continuing.
```

## Rules for every phase

- Pin new dependency versions (look up the latest stable version, do not guess).
- Every behavior change has a test. Every important decision gets an ADR in `docs/decisions/`.
- DTOs only in the REST API, pagination everywhere, no absolute paths or source code in logs.
- Keep the app runnable at the end of each phase.
- Update `README.md` and `docs/` when commands or structure change.

---

## Phase 0: Foundation (finish)

**Goal:** everything builds and starts from the README commands.

**Scope**
- `infrastructure/docker-compose.yml`: Postgres + pgvector, Kafka under profile `pipeline`.
- Liquibase master changelog in `backend`. First changeset: `CREATE EXTENSION IF NOT EXISTS vector`.
- `backend` datasource from `application.yml`, with environment variable overrides.
- `README.md` (run commands for every module), `AGENTS.md` (spec section 0 + current phase + commands), copy of the spec in `docs/`.
- `docs/decisions/` with the ADRs from spec section 2.2.
- `.gitignore`: `.venv/`, `target/`, `node_modules/`, `.env`, IDE files.
- Layout decision applied: option A means a root `pom.xml` with modules `backend`, `analysis-core`, `analysis-worker`, `miner`.

**Done when**
- `docker compose up -d` starts Postgres.
- `mvn -q verify` passes (from the root for option A).
- `GET /actuator/health` returns `UP`.
- `ng build` passes and `pytest` runs in `spark-jobs` (zero tests is fine).

**Manual check:** open the health URL in the browser.

---

## Phase 1: Core slice, register, analyze, see findings

**Goal:** register a local Java repo, analyze it, and see findings with file/line evidence in the browser.

**Scope**
- `analysis-core`: `LanguageAdapter` interface (spec 3.1) and `JavaAdapter` as the only implementation: JavaParser symbols, PMD analysis, `normalizeSnippet`, fingerprint, `FindingNormalizer`.
- `backend`:
  - `SafePathValidator`: allowed roots from config, canonical paths, no symlink escape, never execute repo code.
  - File discovery with ignore rules (`.git`, `target`, `build`, `node_modules`) and limits (file size, file count, parse time).
  - A `JobDispatcher` interface. Phase 1 implementation: in-process executor. (Kafka implementation comes in Phase 4.)
  - Liquibase tables: `repository`, `analysis_run`, `job`, `file_record`, `symbol`, `finding`.
  - Endpoints from spec section 8: register/list repositories, start analysis, get run status, list findings (filters: severity, rule, file; pagination).
  - Parse failures are counted in the run (files discovered / parsed / failed) and never hidden.
- `frontend`: Repositories screen and Quality screen (findings table, filters, source preview). Loading, empty and error states.
- `sample-data/`: fixture Java repo with an interface and implementation, a controller → service → repository chain, a known smell, an empty catch, a file with a syntax error, and an ignored folder.

**Done when**
- Register the fixture repo, run an analysis, and see the expected findings in the browser.
- The syntax-error file shows as a parse failure and the run still completes.
- Tests cover path traversal, symlink escape, ignore rules, fingerprint stability, and the fixture findings.

**Out of scope:** Kafka, AI, graph, history.

**Manual check:** register the fixture repo in the UI and compare the findings with the fixture's known problems.

---

## Phase 2: AI gateway and structured output

**Goal:** one model interface, several hosted providers, reliable structured output.

**Scope**
- `backend/ai/gateway`: `LlmClient` (`chat`, `structured`, `embed`), `ModelRegistry`, `UsageRecorder`.
- Adapters: Gemini (free tier for testing) and one more hosted provider (OpenAI-compatible HTTP is fine).
- `llm_model` table and endpoints `GET /api/models`, `PUT /api/models/{id}`. API keys come only from environment variables.
- Structured output: JSON schema in the prompt, parse, validate, retry once with the validation error, log both attempts.
- Timeouts, retries with backoff (Resilience4j or Spring Retry), cancellation.
- Usage recording: tokens, latency, estimated cost.
- Secret redaction before any prompt is sent. Prompt templates stored as versioned files.
- Temporary endpoint `POST /api/findings/{id}/explanations` with a `modelId` (replaced by suggestions in Phase 3).
- Settings screen: model list, enable/disable, default model, AI on/off switch.

**Done when**
- The same prompt runs on two different models.
- A schema violation is retried and both attempts are logged.
- Usage rows are stored. With AI switched off, analysis still works.
- Tests use WireMock fake providers (no real API calls in tests).

**Manual check:** explain one finding with two models and compare the answers and the recorded usage.

---

## Phase 3: RAG and suggestions

**Goal:** evidence-cited fix suggestions with an automatic validation verdict.

**Scope** (spec 7.2 and 7.3)
- `SymbolChunker`: one chunk per method/class from the symbol table, with a header (qualified name, file, signature).
- `code_chunk` table with a pgvector column and an HNSW index. Store `embed_model`.
- `EmbeddingService` through `LlmClient.embed()`.
- `Retriever` with a token budget: the enclosing method, direct callers and callees (if available), top-k similar chunks, the rule description.
- `SuggestionService` and `PatchValidator`: schema check, evidence references exist, diff applies in memory, patched file parses, PMD re-run, verdict `{schemaOk, evidenceOk, diffApplies, parses, targetFindingGone, newFindings[]}`.
- Endpoints: `POST /api/findings/{id}/suggestions`, `POST /api/suggestions/{id}/review`.
- `suggestion` table with prompt version, request hash, tokens, latency, verdict.
- AI Review screen: evidence, explanation, diff, verdict, accept/reject/defer.
- Prompt-injection defense: code and comments are data, never instructions.

**Done when**
- A suggestion shows cited evidence, a diff and a verdict.
- A fixture file with "ignore previous instructions" in a comment does not change the behavior.
- Works with AI disabled (analysis still works). Patches are never applied automatically.

**Manual check:** ask for a fix for the empty-catch finding and read the verdict.

---

## Phase 4: Kafka pipeline

**Goal:** analyze many repositories in parallel through Kafka.

**Scope** (spec section 5)
- Topics `repo.analysis.requested.v1`, `repo.commit.discovered.v1`, `analysis.findings.v1`, and DLQs. Event envelope with `schemaVersion`.
- Kafka implementation of `JobDispatcher` in `backend` (replaces the in-process one).
- `analysis-worker`: consumer group, handlers for both request topics, analysis by commit blob with JGit (no checkout), idempotent upserts on `(repository_id, commit_hash, file_path, side, analyzer_version, ruleset_hash)`.
- `miner`: clones allowlisted public repositories over HTTPS, walks commits (configurable window), publishes `repo.commit.discovered.v1` with changed Java files only.
- Bounded retries, then DLQ.
- `commit_record` table. Endpoints `POST /api/corpus/ingest`, `GET /api/corpus/status`. Corpus status screen.
- Testcontainers tests: duplicate delivery, worker restart, DLQ path.
- `docs/benchmarks.md`: repos, commits, files, rows/sec, worker count, machine, and a single-process baseline.

**Done when**
- 20+ repositories are ingested in parallel.
- The duplicate-delivery and restart tests pass.
- Throughput numbers are recorded with the dataset named.

**Manual check:** ingest a small list of repos and watch the status screen.

---

## Phase 5: Spark

**Goal:** a Parquet lakehouse, analytics, and the fix-triple dataset.

**Scope** (spec section 6)
- `spark-jobs/streaming/findings_to_bronze.py`: Kafka → Parquet bronze, with checkpoint.
- Batch jobs: `silver_clean`, `rule_distribution`, `time_to_fix`, `quality_trends`, `hotspots`, `fix_triples`, `load_gold`.
- `fix_triples` rules from spec 6.2. Split by repository. Versioned `dataset_version` and a dataset card (counts, rules, licenses, filters).
- `load_gold` writes `agg_*` tables in Postgres. The backend never queries Spark.
- Analytics endpoints and screen. Every chart drills down to records.
- pytest tests with small known Parquet inputs and hand-computed results.

**Done when**
- Aggregates match the hand-computed fixtures.
- A dataset card exists with real row counts and sizes.

**Manual check:** open the Analytics screen and spot-check one number against the data.

---

## Phase 6: Evaluation harness

**Goal:** measure models objectively.

**Scope** (spec 7.4)
- `ai-lab/eval`: runs the test split of fix triples against each model/prompt condition.
- Metrics: schema-valid, diff-applies, parses, **target-finding-resolved** (primary), no-new-findings, diff size, latency, tokens, cost.
- Conditions: no retrieval vs RAG; hosted model A vs B vs C.
- `benchmark_run` table, `GET /api/benchmarks`, `POST /api/findings/{id}/compare-models`.
- Benchmark and Model Compare screens (leaderboard, per-condition breakdown, sample sizes).
- Small hand-reviewed set (30 to 50 samples) for explanation quality.
- Reproducible: fixed seeds, versioned dataset and prompt version.

**Done when:** at least 3 conditions are ranked on the held-out set, with sample sizes and limits written down.

**Manual check:** compare two models on one finding in the UI.

---

## Phase 7: Fine-tuning

**Goal:** fine-tune a small open code model and measure it honestly.

**Scope** (spec 7.5)
- `ai-lab/finetune`: Colab/Kaggle notebook and scripts for QLoRA (Hugging Face `transformers`, `peft`, `trl`). Train on the training split only.
- Evaluate on held-out repositories with the Phase 6 harness, in the notebook.
- Optional: host the merged model and register it in `llm_model` as `FINETUNED`.
- A short write-up: what worked, what did not, with numbers.

**Done when:** the fine-tuned model has a row on the leaderboard (or the write-up explains why it was not hosted).

**Note:** you run the training on Colab/Kaggle. The agent prepares the notebook and scripts, and reports only real metrics.

---

## Phase 8: Graph, history, polish

**Goal:** code map, snapshot comparison, hotspots, and a demo anyone can reproduce.

**Scope**
- `relationship` table with resolution status and confidence. Unresolved edges are recorded and shown.
- Graph API with server-side limits. Code Cosmos screen with Cytoscape.js 2D: search, cluster by package, progressive expansion, table fallback, optional AI summary of a package or class.
- Impact endpoints (dependencies, dependents, impact).
- `finding_transition` and snapshot comparison (NEW / RESOLVED / PERSISTENT / UNCERTAIN). Never mark RESOLVED if the file failed to parse.
- Hotspot score with visible components. UI states it is a triage heuristic.
- README with screenshots and a demo script.

**Done when:** a new user can reproduce the demo from the README.

---

## Phase 9: Commit Review

**Goal:** an evidence-based review for every new commit.

**Scope** (spec 7.6)
- Git polling for new commits in a registered local repo, using `repo.commit.discovered.v1`.
- `CommitReviewService`: facts (no AI), risk signals (rules, shown with their parts), optional AI summary with model choice.
- `commit_review` table. Endpoints from spec section 8. Commit Review screen.
- AI claims must cite file and lines, and cited references are verified.
- Tip: a lite version (NEW/RESOLVED findings + AI summary between two commits) can be built right after Phase 3.

**Done when:** you pick two commits and get a review where every claim has evidence. With AI off, facts and risk signals still show.

---

## Phase 10: Second language, TypeScript

**Goal:** prove the language-adapter design.

**Scope**
- `TypeScriptAdapter` (Tree-sitter + ESLint) at support level 1 to 2: findings, symbols, AI explanation. No full call graph.
- Shared adapter contract test suite that Java and TypeScript both pass.
- UI shows the support level per language.

**Done when:** a small Angular repo is analyzed through the same pipeline with no changes to Kafka, Spark, AI or backend code.

---

## Post-Phase 10: Repository lifecycle (deletion)

Not a planned phase — found by walking the running app, where a wrongly registered path could not be removed from
the browser and re-registering it is refused by design.

**Scope**
- `DELETE /api/repositories/{id}`: one statement, cascaded by the database, 204 on success, 404 for an unknown id.
- Refuse with 409 while one of the repository's runs is still `PENDING` or `RUNNING` (deleting underneath a runner
  is a race).
- Changeset `0024-commit-review-repository-cascade.yaml`: the one repository child foreign key without
  `ON DELETE CASCADE` gains it, so the delete stops failing once a commit review exists.
- Delete control with an inline confirmation on the Repositories screen; nothing is sent until it is confirmed, and
  a refusal keeps the row and shows the backend's message.

**Done when:** a mis-registered repository can be removed from the UI and its path registered again, with the runs,
findings and commit reviews it produced gone and the folder on disk untouched
([ADR 0037](decisions/ADR-0037-repository-deletion.md)).

---

## Phase 11: Providers and models as data

**Goal:** adding a vendor is a row, not a deployment — "I want to use DeepSeek / Claude with my own key" is an
operator action in the browser.

**Scope**
- `llm_provider` table (changeset `0025-create-llm-provider.yaml`): `code`, `protocol` (selects the adapter),
  `base_url`, and a credential that is either an environment variable name or a key pasted into the screen and
  stored encrypted (AES-256-GCM under `BCOSMOS_AI_SECRET_KEY`, the provider code as associated data).
- `llm_model.provider_id` foreign key replaces the compile-time `provider` enum column; uniqueness becomes
  `(provider_id, model_name)`; existing rows are backfilled by their old enum value.
- `GET/POST/PUT/DELETE /api/providers` with validation in `ProviderRegistry`; 404 unknown, 409 while models still
  reference the row, 409 when a key must be stored but no master key is configured.
- The credential never leaves the server: responses carry `credentialSource`, a four-character hint and the
  variable name — never the key.
- Settings screen: provider list with add, edit and two-step delete, credential badges on the model registry,
  and an "Add model" form that registers any model id the vendor spells through `POST /api/models` (the endpoint
  existed, the screen did not use it — without it a fresh provider could not be given a model from the browser).
- Seeds: `gemini`, `openai`, `local-openai`, `deepseek`, `claude` (Anthropic through its OpenAI-compatible layer).
- Changeset `0026-fix-seeded-model-notes.yaml`: two seeded `llm_model` rows still told the operator to set a
  `bcosmos.ai.providers.*` key that no longer exists; the old changesets are never edited (checksums), so the note
  is corrected as data, guarded to leave a user-rewritten note alone.

**Done when:** a new provider plus its model can be registered from the UI with a pasted key, listed with its
credential source and hint, edited, and refused for deletion while a model still points at it — with the same
analysis, suggestions and benchmarks working through it afterwards.
([ADR 0038](decisions/ADR-0038-provider-registry-in-the-database.md)).
