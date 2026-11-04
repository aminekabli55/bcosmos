# ADR 0015 — Java 21 (LTS) for `backend`, `analysis-core`, `analysis-worker`, `miner`

- **Status:** Accepted
- **Date:** 2026-10-12
- **Phase:** 0
- **Source:** spec v2 §2.2

## Context

Every Java dependency chain here (Spring Boot, Kafka clients, PMD, JavaParser, Spark's JVM, JGit, Testcontainers)
has to support the chosen language level, and the toolchain must be installable on a normal developer machine.

## Decision

Java **21 (LTS)** (`maven.compiler.release=21`), with the JDK installed at
`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` on the development machine and Maven 3.10.0 through the
Maven wrapper. Java 25 is possible later, but only on 25.0.3 or newer and only after checking each tool.

## Consequences

- Records, pattern matching and virtual threads are available; the Initializr output already targets 21.
- The README documents the JDK requirement, because neither `java` nor `mvn` is on the PATH of a plain shell here;
  the wrapper handles Maven, not the JDK.
- Upgrading to a newer LTS is an explicit ADR, not a silent compiler change.
