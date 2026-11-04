# ADR 0017 — Spark runs in Docker, not as a local install

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0 (infrastructure; used from Phase 5)
- **Source:** spec v2 §2.2, §6; amends [ADR 0004](ADR-0004-spark-analytics.md) and [ADR 0005](ADR-0005-pyspark.md)

## Context

ADR 0004 chose Spark for corpus analytics and noted that "on Windows, Spark needs Java on the PATH and is best run
in Docker/WSL2 to avoid Hadoop `winutils` problems". ADR 0005 pinned the jobs to PySpark but left the runtime
unstated, so the workspace ended up with a PySpark install in `spark-jobs/.venv` (`pyspark==4.2.0`) that runs
everything in local mode. That works, but it is not what Phase 5 is meant to demonstrate: the phase asks for a real
pipeline with a cluster, partitioned Parquet layers and throughput numbers, and it makes every developer's machine
a prerequisite (a matching JDK, a matching PySpark, a writable Hadoop working directory).

Docker Desktop is already a documented prerequisite for this project (`infrastructure/docker-compose.yml` runs
Postgres and Kafka, and `backend`'s tests use Testcontainers), so a container runtime is not a new dependency.

## Decision

Spark runs as a **standalone cluster inside Docker**, driven from the same `infrastructure/docker-compose.yml`
under a `spark` profile:

- `spark-master` — `apache/spark:4.2.0-python3`, master UI and RPC; the UI is published on host **7080**, the RPC
  port stays `7077` (see the amendment below).
- `spark-worker` — one worker, 2 cores / 2 GB, UI published on host **7081**.
- `spark-submit` — a one-off client container (`docker compose run --rm spark-submit ...`) with `spark-jobs/`
  bind-mounted at `/opt/jobs` and the lakehouse on the named volume `sparkdata`.

The image tag is pinned to the **same Spark version as `spark-jobs/requirements.txt`** (`4.2.0`), so a job behaves
the same locally and in the container. The `-python3` variant is the Java 21 / Python 3 build.

The Python venv stays, but only for fast, offline `pytest` runs of the transformation functions; it is no longer
the way jobs are executed. Learning and debugging happen against `spark-submit` in Docker.

## Consequences

- One command reproduces the runtime; no local JDK, PySpark or Hadoop `winutils` setup is needed to run jobs.
- Jobs execute where they will be benchmarked (Phase 5), so the throughput numbers in `docs/benchmarks.md` describe
  the real deployment rather than a laptop-local approximation.
- PySpark applications use **client mode**: Spark standalone does not support cluster deploy mode for Python, so
  the driver runs inside the `spark-submit` container and must stay alive for the job's lifetime. Long-running jobs
  therefore run as a `docker compose run` process, not as a detached service.
- The image is large (hundreds of MB). It is pulled only with the `spark` profile, so Phases 0–4 are unaffected.
- Workers must reach the driver container over the compose network; bind-mounting `spark-jobs/` at `/opt/jobs`
  gives every container the same code path, which is also the path passed to `spark-submit`.
- `pytest` must not require a cluster. Transformations stay importable functions tested with small local Parquet
  fixtures, exactly as the phase guide requires.

## Amendment (2026-10-15): the Spark UIs moved off 8080 and 8081

Original decision published the master UI on `8080` and the worker UI on `8081` — Spark's own defaults, mapped
one-to-one. The spec (§10) gives those two ports to the `backend` and the `analysis-worker`, and the Angular dev
server proxies `/api` to `localhost:8080`, so the collision was not hypothetical: with the `spark` profile running,
no backend could start (`Web server failed to start. Port 8080 was already in use`), and the analysis-worker could
not either.

The **host** side of both UI mappings moves; nothing inside the containers changes, and the RPC port stays `7077`, so
workers and `spark-submit` are unaffected:

| Service | Container port | Host port now | Host port before |
|---|---|---|---|
| `spark-master` UI | 8080 | **7080** | 8080 |
| `spark-master` RPC | 7077 | 7077 | 7077 |
| `spark-worker` UI | 8081 | **7081** | 8081 |

The rule this encodes: the fixed application ports (backend 8080, analysis-worker 8081, miner 8082) are the ones that
win, and infrastructure services — whose UIs are for humans watching a cluster — take ports that cannot collide with
them. Verified after the change: `http://localhost:7080/json/` reported `aliveworkers: 1`, the worker UI answered on
`7081`, and the backend started on its default `8080` with `/actuator/health` `UP`.
