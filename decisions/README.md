# Architecture decision records

One file per material decision, named `ADR-NNNN-short-title.md`. Every phase that changes an architecture
assumption adds or updates one (spec §0 rule 7, phase guide "Rules for every phase").

| ADR | Decision | Status |
|---|---|---|
| [0001](ADR-0001-backend-modular-monolith.md) | `backend` is a modular monolith; `analysis-worker` and `miner` are separate processes | Accepted |
| [0002](ADR-0002-postgres-pgvector.md) | PostgreSQL + pgvector is the canonical store | Accepted |
| [0003](ADR-0003-kafka-events.md) | Kafka for commit/analysis events | Accepted |
| [0004](ADR-0004-spark-analytics.md) | Spark for corpus analytics and dataset building | Accepted |
| [0005](ADR-0005-pyspark.md) | PySpark for the Spark jobs | Accepted |
| [0006](ADR-0006-python-scope.md) | Python only in `ai-lab` and `spark-jobs` | Accepted |
| [0007](ADR-0007-llm-client-gateway.md) | LLM access only through a provider-neutral `LlmClient` | Accepted |
| [0008](ADR-0008-cytoscape-2d-graph.md) | Cytoscape.js (2D) for the code graph | Accepted |
| [0009](ADR-0009-liquibase-migrations.md) | Liquibase for migrations, owned by `backend` | Accepted |
| [0010](ADR-0010-json-event-envelope.md) | JSON events with an explicit envelope and `schemaVersion` | Accepted |
| [0011](ADR-0011-analyze-commit-blobs.md) | Analyse files by commit blob (JGit), not by checkout | Accepted |
| [0012](ADR-0012-language-adapter.md) | Language support through `LanguageAdapter` | Accepted |
| [0013](ADR-0013-hosted-llm-apis.md) | All LLMs through hosted APIs, no local model | Accepted |
| [0014](ADR-0014-one-git-repository.md) | One Git repository `bcosmos` (root multi-module build) | **Deferred** |
| [0015](ADR-0015-java-21-lts.md) | Java 21 (LTS) for the Java modules | Accepted |
| [0016](ADR-0016-per-module-repositories-for-now.md) | Keep per-module repositories for now; no root `pom.xml` | Accepted (in force) |
| [0017](ADR-0017-spark-in-docker.md) | Spark runs in Docker (standalone, `spark` profile), not as a local install | Accepted |
| [0018](ADR-0018-configuration-profiles.md) | Backend configuration: one global file plus dev (default) and prod profiles; tests on the test classpath | Accepted |
| [0019](ADR-0019-ai-gateway.md) | AI gateway: policy in one place, a provider adapter per shape, opt-in per model, validated JSON, `llm_usage` | Accepted |
| [0020](ADR-0020-stored-ai-switch.md) | The AI master switch is a stored setting (`app_setting`) with the deployment property as its default | Accepted |
| [0021](ADR-0021-rag-and-validated-suggestions.md) | Retrieval, suggestions, and a validation verdict BCosmos computes itself; patches are never applied | Accepted |
| [0022](ADR-0022-event-contract-in-analysis-core.md) | The Kafka event contract (envelope, types, payloads, topics) lives in `analysis-core`; `repo.mine.requested.v1` added | Accepted |
| [0023](ADR-0023-corpus-ingest-and-mined-repositories.md) | Corpus ingest: allowlist at ingest time, per-URL rejections, mined repositories without a local path, idempotent `commit_record` | Accepted |
| [0024](ADR-0024-mined-repo-analysis-policy.md) | Mined repository analysis policy: commit events only, reject on-demand runs with HTTP 422 | Accepted |
| [0025](ADR-0025-shared-pipeline-port.md) | Shared analysis pipeline and FileSource port in analysis-core | Accepted |
| [0026](ADR-0026-dlq-semantics.md) | Kafka retry semantics and Dead Letter Queue (DLQ) routing | Accepted |
| [0027](ADR-0027-idempotent-upsert-shape.md) | Idempotent consumer sinks and worker_file_result table | Accepted |
| [0028](ADR-0028-spark-gold-layer-and-analytics-api.md) | Spark gold tables, the dataset version and the analytics API | Accepted |
| [0029](ADR-0029-kafka-dual-listeners.md) | Two Kafka listeners: one for host clients, one for containers | Accepted |
| [0030](ADR-0030-qlora-fine-tuning-and-evaluation.md) | QLoRA fine-tuning, evaluation, and leaderboard integration | Accepted |
| [0031](ADR-0031-code-cosmos-graph-and-snapshot-history.md) | Code Cosmos graph architecture, deterministic hotspots, and snapshot history comparison | Accepted |
| [0032](ADR-0032-commit-review.md) | Three-layer evidence-based commit review architecture | Accepted |
| [0033](ADR-0033-typescript-adapter.md) | TypeScript language adapter and polyglot pipeline | Accepted |
| [0034](ADR-0034-repository-primary-language.md) | A repository's primary language is detected from the files a run analysed | Accepted |
| [0035](ADR-0035-run-tool-metadata-from-dominant-adapter.md) | A run's tool metadata comes from the adapter that did the work | Accepted |
| [0036](ADR-0036-parser-binding-as-direct-dependency.md) | A parser binding is a direct dependency of the app that instantiates the adapter | Accepted |
| [0037](ADR-0037-repository-deletion.md) | Removing a repository is a first-class cascading operation (`DELETE /api/repositories/{id}`) | Accepted |
| [0038](ADR-0038-provider-registry-in-the-database.md) | A provider (endpoint + credential) is a database row, encrypted with an environment master key, managed from Settings | Accepted |
| [0039](ADR-0039-development-master-key.md) | The master key is generated into a local key file in the dev profile; the environment still wins | Accepted |
| [0040](ADR-0040-rate-limit-backoff.md) | A rate limit is its own failure (`rate-limited`), and the provider's own wait beats the configured backoff | Accepted |
| [0041](ADR-0041-rate-limit-patience-for-a-sequence.md) | The index build sits out its own rate limits (`bcosmos.retrieval.rate-limit-patience`); one call still fails fast | Accepted |
| [0042](ADR-0042-cosmic-design-system.md) | One cosmic design system in `styles.scss`; legacy tokens aliased, a sidebar shell, and a named motion vocabulary | Accepted |
| [0043](ADR-0043-bounded-relationship-text.md) | Relationship names are bounded at the extractor and the entity, not moved to a search engine | Accepted |
| [0044](ADR-0044-bounded-resumable-index-builds.md) | An embedding index is built in bounded, resumable slices instead of all-or-nothing | Accepted |
| [0045](ADR-0045-gemini-structured-calls-in-2026.md) | Gemini structured calls need a content turn; the seed names a reachable model; 90 s and 4096 tokens | Accepted |
