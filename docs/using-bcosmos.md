# Using BCosmos — a complete walkthrough

This is the "how do I actually use it" document. It assumes you have never read the spec and that you have just
cloned the workspace. Read sections 1–2 to understand what is what, section 3 to see which parts you actually run,
section 4 to start them, section 5 to get past the **Local path** field that confused you, and section 6 to walk
the ten screens in the order they are meant to be used.

Companion documents, in increasing depth: [README.md](../README.md) (commands), [docs/BCOSMOS_PHASES.md](BCOSMOS_PHASES.md)
(what was built in each phase), [docs/BCOSMOS_SPEC_v2.md](BCOSMOS_SPEC_v2.md) (the full specification),
[docs/AGENT_HANDOFF.md](AGENT_HANDOFF.md) (known traps), [docs/decisions/](decisions/README.md) (why things are the
way they are).

---

## 0. Quick start — the short version

Two environment lines and four commands, then three clicks. Sections 3–6 explain every one of them, and section 5
explains the path you have to type.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH:/c/Users/amine/AppData/Local/Programs/DockerDesktop/resources/bin"
WS=/c/Users/amine/Desktop/workspace/Bcosmos

cd $WS/infrastructure  && docker compose up -d            # Postgres; wait for "healthy"
cd $WS/analysis-core   && ./mvnw install -DskipTests      # the shared library (once per clone)
cd $WS/backend         && ./mvnw spring-boot:run          # 8080 — terminal 1, leave it running
cd $WS/frontend        && npm install && npm start        # 4200 — terminal 2, leave it running
```

Then, in the browser at <http://localhost:4200>:

1. **Repositories** → paste `C:\Users\amine\Desktop\workspace\Bcosmos\sample-data\java-fixture` into *Local path* →
   **Register** → **Analyse**.
2. Wait for the run to go `RUNNING` → `SUCCEEDED`, then press **Findings**.
3. On **Quality**, click the finding row to read its evidence. That is the whole first loop of the product.

`analysis-worker`, `miner`, Spark, Kafka, `ai-lab` and the notebook are **not** needed for that loop — see section 3
for which ring each one belongs to.

---

## 1. What BCosmos is, in six lines

1. You point it at **source code on your machine** (or at public Git repositories it clones for you).
2. It **reads** that code — it never builds it, never runs it, never installs anything from it. There is no `npm
   install`, no `mvn`, no test execution anywhere in the pipeline.
3. It produces **findings**: rule violations with file, line range and a code snippet (PMD rules for Java, ESLint
   rules for TypeScript).
4. It builds a **symbol graph** of that code (`CALLS`, `EXTENDS`, `IMPLEMENTS`, `USES_TYPE`), which powers the
   blast-radius view and the hotspot list.
5. It can ask a **hosted AI model** for a fix for one finding, with cited evidence, a diff and a machine-checked
   verdict. Patches are **never** applied to your files.
6. It keeps the results, so you can **compare two runs** of the same repository, review a commit, and measure which
   model fixes the most findings — that last part is the research project.

The whole thing is one Spring Boot backend, one Angular frontend, two small Kafka-side modules and an offline
data/ML side. **Nothing in the app needs an API key**: the AI layer is optional and off by default.

---

## 2. The mental model: three independent rings

```text
  RING A — local analysis  (this is what almost every screen uses)
  ┌────────────────────────────────────────────────────────────────────────┐
  │  browser :4200 ──(proxy /api)──► backend :8080 ──► Postgres (Liquibase) │
  │                                       │                                │
  │                                       └─ analysis-core (library)       │
  │                                          JavaParser + PMD (Java)       │
  │                                          Tree-sitter + ESLint (TS)     │
  └────────────────────────────────────────────────────────────────────────┘

  RING B — corpus pipeline  (only for "mine repositories in bulk")
  ┌────────────────────────────────────────────────────────────────────────┐
  │  backend ──► Kafka ──► miner :8082  (clones repo, records commits)     │
  │                   └──► analysis-worker :8081 (analyses commit blobs)   │
  │                                │                                       │
  │                                └── both write to the same Postgres     │
  └────────────────────────────────────────────────────────────────────────┘

  RING C — lakehouse and models  (batch, not a service)
  ┌────────────────────────────────────────────────────────────────────────┐
  │  Postgres ──► Spark jobs (Docker) ──► Parquet bronze/silver/gold       │
  │                                     └─► agg_* tables back in Postgres  │
  │  ai-lab: dataset splits, evaluation harness, QLoRA notebook            │
  └────────────────────────────────────────────────────────────────────────┘
```

You can use Ring A alone, forever, and ignore B and C. Ring A is Phases 1–3 and 8–10 of the project. Ring B is
Phase 4. Ring C is Phases 5–7.

**The three rings are connected only through Postgres.** That is why the backend never has to talk to Spark: the
Spark jobs write `agg_*` tables and the backend just reads them.

---

## 3. The pieces: what to run, and what not to run

| Folder | What it is | Do you run it? | Port | How to tell it is alive |
|---|---|---|---|---|
| `frontend` | Angular 22 app, 10 screens | **yes** — always | 4200 | <http://localhost:4200> renders |
| `backend` | Spring Boot API, owns the schema, runs local analyses | **yes** — always | 8080 | `curl localhost:8080/actuator/health` → `"status":"UP"` |
| `analysis-core` | plain Java **library**: language adapters, PMD, Tree-sitter, fingerprinting | **no** — it is a jar, nothing to start | – | built by `./mvnw install` |
| `analysis-worker` | Spring Boot Kafka consumer for Ring B | only for Ring B | 8081 | `curl localhost:8081/actuator/health` |
| `miner` | Spring Boot JGit crawler for Ring B | only for Ring B | 8082 | `curl localhost:8082/actuator/health` |
| `infrastructure` | `docker-compose.yml`: Postgres+pgvector, Kafka, Spark | **yes** — Postgres always | 5432/5433, 9092, 7077 | `docker compose ps` |
| `spark-jobs` | PySpark streaming + batch jobs (Ring C) | runs **inside Docker**, never on the host | – | job exits 0, `agg_*` rows appear |
| `ai-lab` | dataset splits, evaluation harness, QLoRA notebook, prompts | **no** — offline scripts and notebooks | – | `pytest -q` prints 28 passed |
| `docs` | spec, phase guide, ADRs, this file | no | – | – |
| `sample-data` | two fixture projects to analyse | no — you *register* them | – | see section 4 |

There is **no root `pom.xml`**: every Java folder is its own Maven project (and its own Git repository for now).
`analysis-core` must be installed into `~/.m2` before `backend`, `analysis-worker` or `miner` can build.

### The 10 screens

| Nav label | Route | What it answers |
|---|---|---|
| Repositories | `/repositories` | which code is registered, and run an analysis |
| Quality | `/quality` | what was found, with file/line evidence |
| (from Quality) AI Review | `/quality/:runId/findings/:findingId/review` | what a model suggests, and whether the patch checks out |
| Cosmos | `/cosmos` | how the code is connected (call graph), and what is risky |
| History | `/history` | what changed between two runs of the same repository |
| Corpus | `/corpus` | bulk mining of public repositories through Kafka |
| Analytics | `/analytics` | aggregates over the whole corpus (Spark gold tables) |
| Benchmarks | `/benchmarks` | which model/prompt condition wins, with sample sizes |
| Reviews | `/commit-reviews` | an evidence-based review of a commit or commit range |
| Settings | `/settings` | the AI master switch, provider keys, model registry |

---

## 4. Start it

### 4.0 One-time environment

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export PATH="$JAVA_HOME/bin:$PATH:/c/Users/amine/AppData/Local/Programs/DockerDesktop/resources/bin"
```

The second entry puts the `docker` CLI on the PATH of this shell; without it `docker compose` says
"command not found" even though Docker Desktop is running. The project is pinned to **Java 21** — if your IDE is
configured with another JDK, you will get strange classpath errors (see section 9).

Docker Desktop must be **running** before anything else: `docker version --format '{{.Server.Version}}'`.

### 4.1 Infrastructure (always)

```bash
cd infrastructure
docker compose up -d                     # Postgres + pgvector  (5432 and 5433)
docker compose ps                        # wait for "healthy"

# only if you want Ring B (bulk mining):
docker compose --profile pipeline up -d  # + Kafka (9092)

# only if you want Ring C (analytics):
docker compose --profile spark up -d spark-master spark-worker
docker compose --profile spark run --rm warehouse-init    # once per volume
```

### 4.2 Build the shared library (once, and after changing `analysis-core`)

```bash
cd analysis-core && ./mvnw install -DskipTests
```

### 4.3 Run the modules

Four terminals, in this order:

```bash
cd backend         && ./mvnw spring-boot:run    # 8080 — creates/migrates the schema on startup
cd analysis-worker && ./mvnw spring-boot:run    # 8081 — only needed for Ring B
cd miner           && ./mvnw spring-boot:run    # 8082 — only needed for Ring B
cd frontend        && npm start                 # 4200
```

Check them:

```bash
curl -s localhost:8080/actuator/health | head -c 200      # "status":"UP", db UP
curl -s localhost:8081/actuator/health
curl -s localhost:8082/actuator/health
```

Then open <http://localhost:4200>. The frontend talks to the backend through a dev-server proxy that forwards
`/api` to `http://localhost:8080`, so **the backend must be on 8080** or every screen shows an error state.

---

## 5. The input you were stuck on: "Local path"

This is the single most confusing field in the app, so here it is in full.

**It is a folder path on the computer that runs the backend.** Not a URL, not a GitHub link, not a file, not a
project name.

| Question | Answer |
|---|---|
| Which path? | The **folder** that contains the code, e.g. the folder that holds `src/` |
| Relative or absolute? | Either works, but a relative path is resolved against the **backend's** working directory, so use an absolute path |
| Forward or back slashes? | Both work on Windows (`C:/Users/...` and `C:\Users\...` are the same path). Inside a JSON body you must escape backslashes as `\\` |
| Any restriction? | The canonical path must be **inside an allowed root**. In the `dev` profile the default allowed root is your home directory, i.e. `C:\Users\amine`. A path outside it is rejected with HTTP 400 |
| Can I widen it? | Yes: `BCOSMOS_ALLOWED_ROOTS="C:/code;C:/work"` before starting the backend (semicolon-separated) |
| Does it need to be a Git repository? | No for analysis. **Yes** for the Reviews screen, which needs commits |

### Copy-paste examples for the two fixtures

| "Local path" | "Name (optional)" | What a run must produce |
|---|---|---|
| `C:\Users\amine\Desktop\workspace\Bcosmos\sample-data\java-fixture` | `java-fixture` | 7 files discovered, 6 parsed, **1 failed**, **1 finding** (`EmptyCatchBlock`, `IngestRunner.java:22`), 28 symbols |
| `C:\Users\amine\Desktop\workspace\Bcosmos\sample-data\angular-fixture` | `angular-fixture` | 4 files discovered, 3 parsed, **1 failed**, **4 findings** (`no-console`, `no-unused-vars`, `no-explicit-any`, `no-empty`), 12 symbols |

The parse failure is deliberate: each fixture contains a file with broken syntax (`SyntaxError.java`,
`syntax-error.ts`). A run must record it and still finish — that behaviour is part of the specification.

Use **your own project** the same way if you prefer: any folder you can read, under your home directory.

### Errors this field can produce

| Message | HTTP | Cause |
|---|---|---|
| `a repository path is required` | 400 | blank input |
| `the repository path does not exist` | 400 | typo in the path |
| `the repository path is not a directory` | 400 | you pointed at a file |
| `the repository path is outside the allowed roots configured for this installation` | 400 | outside `C:\Users\amine`; set `BCOSMOS_ALLOWED_ROOTS` |
| `this repository path is already registered as 'java-fixture'` | 409 | it is already in the list — use that row (press **Analyse** on it), or delete it first if you really want a fresh registration |

### Removing a repository

From Phase 10 on, a wrong registration is recoverable from the browser. In the *Registered repositories* table:

1. Press **Delete** on the row.
2. A confirmation line appears under the row naming the repository and saying what goes with it. Press
   **Yes, remove it** — or **Cancel**, which sends nothing.
3. The row disappears and a green `Removed <name>.` notice appears. Registering that same path works again.

What the delete takes with it is everything recorded *about* that repository: its analysis runs, files, symbols,
findings, suggestions, code chunks, relationships, finding transitions, commit records and commit reviews. The
folder on disk is **not** touched — BCosmos only ever read it, so the project itself is fine.

Two refusals and one caveat:

- **409 `Repository is busy`** while one of its runs is still `PENDING` or `RUNNING`. Wait for the run to finish or
  fail, then delete. (Deleting underneath a runner would race it.)
- **404** if the id is unknown — deleting something already deleted is not an error to retry.
- Deletion is **irreversible**. There is no undo, no archive, and the runs it removes are the rows the **History**
screen compares, so a comparison involving them is gone too. The **Analytics** screen can also keep showing that
repository until the next `load_gold` run: the gold tables are built by Spark from the lakehouse, not from this
table ([ADR 0037](decisions/ADR-0037-repository-deletion.md)).

If the backend is not running, or you want to see exactly what is registered before removing it, the same operation
from SQL. The cascade does the rest, and the folder on disk is untouched either way.

```bash
export PATH="/c/Users/amine/AppData/Local/Programs/DockerDesktop/resources/bin:$PATH"

docker exec infrastructure-postgres-1 psql -U bcosmos -d bcosmos \
  -c "select id, name, source_type, status from repository order by id;"

docker exec infrastructure-postgres-1 psql -U bcosmos -d bcosmos \
  -c "DELETE FROM repository WHERE name = 'java-fixture';"
```

Every table hanging off `repository` cascades, `commit_review` included since changeset
`0024-commit-review-repository-cascade.yaml` (before that changeset a single commit review made this statement fail
with a foreign-key error).

If you only want a different **name**, do not delete anything — rename it:

```bash
docker exec infrastructure-postgres-1 psql -U bcosmos -d bcosmos \
  -c "UPDATE repository SET name = 'java-fixture-2' WHERE id = 1;"
```

---

## 6. The tour, screen by screen

The order below is the order the screens are meant to be used in. Each section says what the screen needs to have
data, so an empty screen never surprises you.

### 6.1 Repositories — register and analyse

1. Type the path (section 5) and an optional name, press **Register**.
2. The row appears in *Registered repositories* with `Source = LOCAL`, a language badge, and status
   `REGISTERED`. Right after registration the language badge says `Java · L3` even for a TypeScript project —
   registration does not read the tree, so it cannot know. **The first run that finishes replaces it** with the
   language most of its files really were written in (that is why `angular-fixture` shows `TypeScript · L2`).
3. Press **Analyse** on that row. A run starts and the *Analyses of …* table appears below.
4. Watch the run: status `RUNNING` → `SUCCEEDED` (the screen polls once a second while a run is moving). Columns:
   `Run`, `Status`, `Stage`, `Discovered`, `Parsed`, `Failed`, `Findings`, `Symbols`, `Started`.
5. Press **Findings** to open the Quality screen for that run. (The **Runs** button just re-opens the table.)

Notes:

- Running **Analyse** again is normal and expected: every run is a new row, and two rows of the same repository are
  what the **History** screen compares.
- **Delete** forgets the repository and everything recorded for it, after an inline confirmation. The folder on
  disk is untouched, and the request is refused while a run of it is still going (section 5).
- A repository registered by the miner shows `Source = MINED` and **refuses** on-demand analysis with HTTP 422:
  `mined repositories are analysed via commit discovery events`. That is deliberate — their runs are produced by
  `analysis-worker` through Kafka, so you never press Analyse on them.
- If the backend has no Kafka (`bcosmos.kafka.enabled=false`, the default), analysis runs inside the backend
  process. You do **not** need the `analysis-worker` for this screen.

### 6.2 Quality — the findings

The run's counters are at the top: `Status`, `Stage`, `Files discovered`, `Parsed`, `Parse failures`, `Findings`,
`Symbols`, `Started`, `Finished`, `Parser`, `Analyzer`.

- **`Parser` / `Analyzer`** tell you which toolchain produced the run, e.g.
  `com.github.javaparser:javaparser-core:3.28.2` + `net.sourceforge.pmd:pmd-java:7.28.0` for Java,
  `io.github.bonede:tree-sitter-typescript:unknown` + `eslint:9.12.0/...` for TypeScript.
- **Files that failed to parse** is its own table, with the file, its language badge and the parse error. Failures
  are counted, never hidden.

In the **Findings** table you can filter by `Severity` (exact value), `Rule` (exact id, with autocompletion) and
`File contains`, then page through the results. Columns: `Severity`, `Rule`, `Message`, `File`, `Lines`, `Symbol`,
`Triage`.

Click a row or press Enter to open the **Evidence** panel on the right: the file and line, the tool and version
that produced it, the original vs normalized severity, the **fingerprint** (a stable hash of rule + file + snippet —
this is what lets two runs be compared), the stored code snippet with the offending lines highlighted, and the
button **Ask AI for a fix**.

### 6.3 AI Review — a suggested patch and its verdict

This screen needs three things, and it tells you which one is missing:

1. the AI master switch **on** (`Settings`);
2. a **chat** model enabled **and** an **embedding** model enabled (the suggestion is built from retrieved code
   chunks, which need embeddings);
3. the provider's API key present in the environment (`GEMINI_API_KEY`, or `OPENAI_API_KEY`).

With a model registered but not enabled you get HTTP 409 and the message
`model 'GEMINI/gemini-2.5-flash' is registered but not enabled; enable it in settings first`; with no key you are
told which environment variable to set. **Nothing else in the app is affected by any of this** — analysis works
with the AI layer completely off.

What the screen shows once a suggestion exists: the model's `Summary`, `Explanation`, the `Cited evidence` it was
allowed to see, `Risks`, `Tests to run`, `Uncertainties`, the `Proposed diff`, the `Validation verdict` (schema
valid, evidence resolvable, diff applies, patched file parses, target finding gone, new findings introduced), and
**what was not checked**. You then choose `ACCEPTED`, `REJECTED` or `DEFERRED` — which also moves the finding's
triage status. **No patch is ever written to your files.**

### 6.4 Cosmos — the code map

Needs: a **Java** analysis run with resolved relationships. TypeScript runs are support level 2 and have no call
graph, so a TypeScript repository shows an almost empty graph — that is expected, not a bug.

1. Pick the repository and the analysis run from the two dropdowns at the top.
2. Explore the 2D graph: zoom, pan, `Layout` (force-directed CoSE, concentric by hotspot score, hierarchical), and
   filter by `Kind`. Search filters nodes by symbol, package or file path. `Max Nodes` caps what is drawn.
3. Click a node to open the inspector: kind, qualified name, line count, findings, hotspot score, and
   **Calculate Blast Radius**, which highlights the transitive callers and dependents on the canvas.
4. The **Architectural Hotspots** panel ranks files/symbols with the exact formula
   `score = (findings × 10) + (highSeverity × 20) + (inDegree × 5) + (lineCount × 0.05)`. It is a triage
   heuristic, not a verdict. Click a row to centre that node.
5. `Accessible Table` gives the same information as a keyboard-navigable table.

Known rough edge: the `Package / File` column and the inspector's file path are **empty today** — the graph API
hard-codes an empty `filePath` for nodes (`GraphService`, `"", // filePath populated when available`). Everything
else on the screen is real data.

### 6.5 History — what changed between two runs

Needs: **two or more runs of the same repository**.

1. Choose the `Baseline Snapshot (From)` and `Target Snapshot (To)` and compare.
2. The stat cards are the transitions: `NEW` (regressions), `RESOLVED` (fixed), `PERSISTENT` (open in both),
   `UNCERTAIN` (could not be decided).
3. Filter by category or search by rule, file or reason.
4. The table explains each row: transition, confidence, rule, file, and the **reason** — including the safeguards.
   The important one: a finding is **never** marked `RESOLVED` if the file failed to parse in the target run, or if
   the rule was disabled in that run. Those become `UNCERTAIN` with the reason spelled out.

### 6.6 Reviews — commit review (three layers)

Needs: commits. A local folder that is **not** a Git repository has none, and the screen says *No commits found* —
that is true of both fixtures. Use a real checkout you register, or a **mined** repository (the miner records its
commits, and the screen reads those).

1. Pick the repository. `Recent commits` lists what is available, newest first.
2. Choose `From commit` and `To commit` (or just one, for a single-commit review) and run the review.
3. Read the three layers:
   - **Layer 1 — Facts** (no AI): changed files, `NEW` / `RESOLVED` / `PERSISTENT` / `UNCERTAIN` findings for the
     snapshot, dependents affected, unresolved edges. Metric cards: files changed, new findings, resolved,
     persistent, dependents affected.
   - **Layer 2 — Risk signals**: explainable heuristics, each shown with its parts — `HOTSPOT_MODIFIED`,
     `NEW_FINDINGS_INTRODUCED`, `MAIN_CHANGED_NO_TESTS` (tagged as heuristic), `HIGH_BLAST_RADIUS`.
   - **Layer 3 — AI summary** (optional, needs the AI steps from 6.3): every file it cites is **verified against
     the real diff**; an invented file is rejected. Choose the model in the `AI model` dropdown.
4. `What to check` and `Suggested tests to run` close the report.

### 6.7 Corpus — bulk mining (Ring B)

Needs: the `pipeline` profile (Kafka) running, the `miner` process, and the backend started with
`BCOSMOS_KAFKA_ENABLED=true` (see 7.1). Without Kafka the request fails with HTTP 500 — the backend refuses to
publish, but only *after* the transaction commits, so the intended "turn the pipeline on" message never reaches you
(rough edge, section 11).

1. Paste one **HTTPS** repository URL per line (`https://github.com/apache/commons-lang`). The host allowlist is
   enforced: `github.com` by default, widen with `BCOSMOS_CORPUS_ALLOWED_HOSTS`.
2. `Commit Window` = how many commits back to walk per repository.
3. Press **Start Mining**. The batch summary tells you what was accepted (and whether it was new) and what was
   rejected, with the reason — a rejected URL is a real rejection, not a silent failure.
4. The `Corpus Progress` table tracks each repository: status, commits recorded, latest commit, last recorded at.
   The metric cards count mined repositories and recorded commits.

Those repositories appear on the Repositories screen as `Source = MINED`, and from there they flow into the corpus
that the Analytics and Benchmarks screens aggregate.

### 6.8 Analytics — the lakehouse read back

Needs: the **Spark chain** to have run `load_gold` (section 7.2). Before that, every widget is in its "no gold
snapshot yet" state — that is a data state, not a broken screen.

Widgets: `Findings by rule` (with `Per KLOC`, files, severity mix), `Quality trend` (findings per KLOC over time,
with parse failures), `Commits until a finding is resolved`, `Triage hotspots` (score, churn, complexity proxy,
open findings, latest revision), and the `Dataset card` (split, triples, repositories, rules, snippet changed).

Every chart drills down: click a bar or a row to see the records behind that number. `Drill-down depth`,
`Repository`, `Rule`, `Clear filters` and `Close` control the drill-down.

### 6.9 Benchmarks — which model actually wins

The leaderboard is one row per measured condition: model/condition, dataset version, `n` (sample size), run
metadata, and the metrics — schema valid, diff applies, parses, **target finding resolved** (the primary metric),
no new findings, median diff size, latency, tokens, cost. Sort by any column, expand a row for the per-rule
breakdown and `Full metrics`, and filter by dataset version. Rows have a bar chart next to the primary metric so
the ranking is visible at a glance.

The rows come from the evaluation harness in `ai-lab` (section 7.3), not from the UI; the UI is read-only here.

### 6.10 Settings — the AI layer

- The master switch (`AI layer on/off`) is stored in the database (`app_setting`) and overrides the
  `bcosmos.ai.enabled` default. The AI layer being on is necessary but not sufficient: a model must also be
  enabled.
- `Provider keys` shows, per provider, which **environment variable** holds the key and whether it is present. The
  key itself is never stored in the database and never displayed.
- `Model registry` lists every registered model with provider, kind, capability, cost per 1k tokens, context
  window, `Credential` (present/absent), `Enabled` and `Default`. You can enable/disable a model and choose the
  default. Enabling a model whose credential is missing is refused, with the variable name to set.

**To make AI Review work with Gemini (free tier):**

```bash
cd backend
GEMINI_API_KEY=<your key> ./mvnw spring-boot:run     # the key is read from the environment only
```

then in Settings enable `gemini-2.5-flash` (chat) **and** `gemini-embedding-2` (embeddings), keeping the master
switch on. Check the registry afterwards: both rows must read `Enabled = yes` and `Credential = present`.

---

## 7. Running the other parts

### 7.1 Ring B — Kafka pipeline

```bash
cd infrastructure && docker compose --profile pipeline up -d     # Kafka 9092
# backend, miner and analysis-worker all running (section 4.3)
```

Kafka is not enough on its own: the backend must be started with the pipeline enabled, otherwise it runs analyses
in-process and refuses to publish anything.

```bash
cd backend && BCOSMOS_KAFKA_ENABLED=true ./mvnw spring-boot:run
```

Then use the **Corpus** screen (6.7). A URL whose host is not allowlisted is returned in the batch summary's
`rejected` list with the reason, so `https://gitlab.com/foo/bar` answers
`host gitlab.com is not in bcosmos.corpus.allowed-hosts` instead of failing silently.

What happens underneath: the backend registers the repository and emits
`repo.analysis.requested.v1` → the **miner** clones over HTTPS (no checkout) and walks the commit window, emitting
`repo.commit.discovered.v1` per commit → the **analysis-worker** reads each commit's blobs and analyses the changed
files, writing idempotent per-file results into `worker_file_result`. Failures retry with bounded exponential
backoff and then land in a DLQ, so a poison message cannot block the stream. Duplicate delivery is safe by design:
re-running a commit updates rows instead of duplicating them.

### 7.2 Ring C — the Spark lakehouse

Spark runs **in Docker** only (there is no working PySpark on the Windows host). Every command below is run from
`infrastructure/`.

```bash
# once per volume (a fresh clone, or after `docker compose down -v`)
docker compose --profile spark run --rm warehouse-init

# 1. stream new findings events into bronze Parquet
docker compose --profile spark run --rm spark-submit --master local[2] \
  --conf spark.jars.ivy=/tmp/.ivy2 \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.13:4.2.0 \
  /opt/jobs/streaming/findings_to_bronze.py \
  --bootstrap-servers kafka:9092 --starting-offsets earliest \
  --trigger-seconds 15 --duration-seconds 60

# 2. the batch chain, in order
for job in silver_clean rule_distribution quality_trends time_to_fix hotspots fix_triples load_gold; do
  docker compose --profile spark run --rm spark-submit --master local[2] \
    --conf spark.jars.ivy=/tmp/.ivy2 \
    --packages org.postgresql:postgresql:42.7.4 \
    /opt/jobs/batch/$job.py
done
```

`load_gold` is the one that writes the `agg_*` tables, and it is what makes the **Analytics** screen come alive.
Two things about it on this machine:

- the first run of a job that uses `--packages` downloads jars from Maven Central, so it can sit silent for a few
  minutes before Spark prints anything;
- the default driver heap is 1 GB and the gold build joins the drill-down tables, so a run can die with
  `java.lang.OutOfMemoryError: Java heap space` (observed on this machine on 2026-10-15). Add
  `--driver-memory 3g --conf spark.sql.shuffle.partitions=4` after `--master local[2]` when that happens.

The Spark job tests run in the same image (`spark-test`), because Spark cannot start on the host:

```bash
docker compose --profile spark run --rm spark-test -q tests
```

### 7.3 `ai-lab` — evaluation and fine-tuning (offline)

```bash
cd ai-lab && ./.venv/Scripts/python.exe -m pytest -q      # 28 tests
```

- `ai-lab/eval` — the evaluation harness: runs the held-out fix-triple test split against a model/prompt condition
  and measures the seven metrics the Benchmarks screen shows. Also has a small hand-reviewed set for explanation
  quality.
- `ai-lab/finetune` — `prepare_dataset.py` (fix-triples → chat-format SFT rows), `generate_notebook.py` and the
  QLoRA notebook, `evaluate_finetuned.py` to score a fine-tuned model, `merge_and_push.py` to merge an adapter.
- `ai-lab/prompts` — the versioned prompt files the backend also uses. Changing one is a prompt version change;
  old rows keep the version that produced them.

---

## 8. Resetting, and re-running

| I want to… | Do this |
|---|---|
| re-analyse the same code | press **Analyse** again on the Repositories screen — a new run row |
| compare before/after a code change | edit the file, run **Analyse**, then use **History** with the two runs |
| get a clean database | `cd infrastructure && docker compose down -v && docker compose up -d`, then restart the backend (Liquibase rebuilds everything, seeded rows included) |
| re-seed Spark gold data | run the batch chain again (7.2) |
| check nothing is broken | `docker compose ps` and the three `/actuator/health` URLs |

---

## 9. Troubleshooting: symptom → cause → fix

| Symptom | Cause | Fix |
|---|---|---|
| `Factory method 'typeScriptAdapter' threw exception … org/treesitter/TSLanguage` | the classpath that launched the app has no `io.github.bonede:tree-sitter`. Maven is fine; an **IDE** library list assembled before Phase 10 is not | Reload the Maven projects in the IDE and rebuild. Control: `cd backend && ./mvnw test -Dtest=AnalysisConfigurationTypeScriptBindingTest` (green = the code is fine, the launcher is stale) |
| `Port 8080 was already in use` | the Spark master UI used to publish 8080, or a second backend is running | `netstat -ano \| grep :8080`, then `docker compose --profile spark up -d --force-recreate spark-master spark-worker` for an old container. Spark UIs are on **7080/7081** now, so the backend owns 8080 |
| `FATAL: password authentication failed for user "bcosmos"` | a native PostgreSQL on the host also listens on `5432` and answers first | the `dev` profile already defaults to **5433** (published by compose); do not set `BCOSMOS_DB_PORT=5432` by hand |
| Every screen shows an error state | the backend is not on 8080, or is not running | start it, then `curl localhost:8080/actuator/health` |
| `model ... is registered but not enabled` (HTTP 409) | the AI steps in 6.3 | enable the chat **and** embedding model in Settings, and provide the provider key |
| I typed a path and got `outside the allowed roots` | the path is not under `C:\Users\amine` | move the code under your home directory, or set `BCOSMOS_ALLOWED_ROOTS` |
| `Analyse` on a mined repository returns 422 | mined repositories are analysed by the worker through Kafka | expected. Run the pipeline (7.1) instead |
| Corpus **Start Mining** returns `Unexpected error` (500) | the backend was started without `BCOSMOS_KAFKA_ENABLED=true`, so it has no event publisher | restart the backend with the pipeline enabled, after starting the `pipeline` profile (7.1) |
| Cosmos is nearly empty | the repository is TypeScript (support level 2, no call graph) | analyse a Java repository to see the graph |
| Reviews says *No commits found* | the registered folder is not a Git repository (both fixtures are not) | register a real checkout, or use a mined repository |
| the frontend opens on a port other than 4200 | `npm start` honours a `PORT` environment variable | `unset PORT` before `npm start`, or use whatever port it printed |
| Analytics shows "no gold snapshot yet" | the Spark chain has not written `load_gold` | run 7.2 |
| a Spark job dies with `java.lang.OutOfMemoryError: Java heap space` | the 1 GB default driver heap is too small for the gold joins | add `--driver-memory 3g --conf spark.sql.shuffle.partitions=4` to the `spark-submit` command |
| a Spark job prints nothing for minutes | `--packages` is downloading jars from Maven Central, and the image keeps its ivy cache in `/tmp` | wait; the next run reuses `/tmp/.ivy2` inside that container only, so expect the download again |
| `docker: command not found` | the Docker CLI is not on this shell's PATH | add `/c/Users/amine/AppData/Local/Programs/DockerDesktop/resources/bin` to `PATH` |
| The IDE runs the app on JDK 25 but Maven uses 21 | the project pins Java 21 | set the project SDK (and run configurations) to `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |

---

## 10. What is in the database right now (this machine, 2026-10-15)

So you can tell "empty because it needs a job" apart from "empty because it is broken":

| Table | Rows | Notes |
|---|---|---|
| `repository` | 5 | `java-fixture` (1), `commons-lang` (2, MINED), `commons-codec` (3, MINED), `commons-text` (4, MINED), `angular-fixture` (5) |
| `analysis_run` | 13 | 4 for `java-fixture`, 9 for `angular-fixture`; the three mined repositories have 0 (by design) |
| `finding` | 39 | 3 across the four `java-fixture` runs (the older runs predate the PMD 7 baseline change), 36 across the nine `angular-fixture` runs; a fresh run today gives 1 and 4 respectively |
| `symbol` / `relationship` | 164 / 37 | Java only for relationships |
| `finding_transition` | 9 | History between the fixture runs |
| `commit_record` | 68 | from the three mined repositories |
| `suggestion` / `code_chunk` | 0 / 0 | needs an enabled embedding model + key |
| `commit_review` | 0 | needs a repository with commits |
| `benchmark_run` | 4 | seeded by the Phase 7 changeset |
| `llm_model` | 5 | all `enabled = false`, no credential configured — so AI Review is off until you do 6.10 |
| `agg_*` (gold) | 41 findings, 86 hotspots, 9 pipeline stats | one Spark snapshot, dataset `v20261014-008357c8` |

---

## 11. Known rough edges (honest list)

- The Cosmos graph API returns an **empty `filePath`** for every node, so the `Package / File` column and the
  inspector show no file. (`GraphService.java`, `"", // filePath populated when available`.)
- An **unknown URL** returns HTTP **500** (`Unexpected error` with a correlation id) instead of 404, and a
  **malformed JSON body** also returns 500 instead of 400. Validation errors are correct (400 with field errors).
  This only bites when you call the API by hand.
- `POST /api/corpus/ingest` with the Kafka pipeline **off** returns that same generic 500 instead of the intended
  409 "set `bcosmos.kafka.enabled=true`": the event is published in an after-commit callback
  (`CorpusService.publishAfterCommit`), so the refusal happens after the response is already an error. Costing one
  clear message, not data — the repository row is still created.
- The TypeScript parser version renders as `io.github.bonede:tree-sitter-typescript:unknown` — the binding does not
  expose its version.
- Deleting a repository is irreversible and does not cancel work already queued on Kafka: the worker routes those
events to the DLQ ([ADR 0026](decisions/ADR-0026-dlq-semantics.md)).
- Triage moves only by reviewing a suggestion, so a finding cannot be accepted or dismissed without asking a model
  first.
- `GET /api/benchmarks` returns a bare array rather than a paginated envelope.
- A native PostgreSQL service still exists on this machine and re-takes `5432` after a reboot; the dev profile
  defaults to 5433 so this does not matter unless you export `BCOSMOS_DB_PORT=5432`.

---

## 12. Where to read more

| Document | Read it for |
|---|---|
| [README.md](../README.md) | every command, environment variable and verification suite |
| [docs/BCOSMOS_PHASES.md](BCOSMOS_PHASES.md) | what each phase added, and the "manual check" for each |
| [docs/BCOSMOS_SPEC_v2.md](BCOSMOS_SPEC_v2.md) | the contract: data model, rules, metrics, acceptance criteria |
| [docs/rule-catalog.md](rule-catalog.md) | the exact Java and TypeScript rules, and what makes each one fire |
| [docs/ai-design.md](ai-design.md) | how the AI gateway, structured output and retries work |
| [docs/dataset-card.md](dataset-card.md) | the fix-triple dataset: counts, filters, licences |
| [docs/benchmarks.md](benchmarks.md) | measured pipeline throughput numbers |
| [docs/fine-tuning-report.md](fine-tuning-report.md) | what the QLoRA fine-tune did and did not achieve |
| [docs/AGENT_HANDOFF.md](AGENT_HANDOFF.md) | the environment traps in detail |
| [docs/decisions/](decisions/README.md) | one ADR per significant decision, with the alternatives |
