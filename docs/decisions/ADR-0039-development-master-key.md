# ADR 0039: A development master key is generated on first start

- **Status:** Accepted
- **Date:** 2026-10-16
- **Phase:** 11 follow-up (walking the running app)
- **Related:** [ADR 0038](ADR-0038-provider-registry-in-the-database.md), [ADR 0007](ADR-0007-llm-client-gateway.md), [ADR 0018](ADR-0018-configuration-profiles.md)

## Context

ADR 0038 made a provider a row with a credential that a user pastes in the Settings screen, encrypted with AES-256-GCM
under a master key that must come from `BCOSMOS_AI_SECRET_KEY`. Walking the running application made the cost of that
visible: the very first thing a new operator does — press *paste key* for Gemini — answered **409 Secret storage not
configured**, with a message telling them to run `openssl rand -base64 32`, put the result in an environment variable
and restart the backend.

That is a reasonable instruction for a deployment and a bad one for a development machine:

- The variable has to reach the process. The backend is normally started from an IDE run configuration, so the fix is
  not "export and restart the shell" but "edit the run configuration, then restart" — a detour through IDE settings
  before the product's own Settings screen will work.
- The failure lands at the *first feature use*, not at startup, so it reads as a broken feature rather than as missing
  configuration.
- The ADR 0038 design was justified by "a user of the running application can connect their own account without shell
  access to the server" — which is exactly the user who could not proceed.

The alternatives were: keep the requirement and document it harder (the wall stays), or let the application hold its own
key locally when nothing else provides one.

## Decision

**The master key is resolved from three sources, in this order** (`MasterKeySource`, wired in `AiConfiguration`):

1. `bcosmos.ai.secret-key` / `BCOSMOS_AI_SECRET_KEY` — the environment. **It always wins**, so a deployment keeps
   saying where its key is and no file decides for it.
2. The file named by `bcosmos.ai.secret-key-file` / `BCOSMOS_AI_SECRET_KEY_FILE`, if it holds a key.
3. A key **generated on first start** and written to that file — only when `bcosmos.ai.generate-secret-key` /
   `BCOSMOS_AI_GENERATE_SECRET_KEY` is on.

Supporting decisions:

- **Off outside development.** The global `application.yml` sets `secret-key-file` empty and
  `generate-secret-key: false`; only `application-dev.yml` (the default profile, ADR 0018) points the file at
  `${user.home}/.bcosmos/ai-secret.key` and turns generation on. A production deployment is therefore still the
  environment-only deployment of ADR 0038 and spec §10: it states its key and never invents one on a container
  filesystem, where the file would not survive the next container anyway.
- **The file is outside the repository** — the user's home directory, the same place as `.aws`/`.ssh` — so it cannot be
  committed by accident, and the workspace's own per-module Git repositories never see it.
- **The generated key is ordinary key material**: 32 random bytes from `SecureRandom`, base64, exactly what the
  environment variable holds. `SecretCipher` treats the two identically and no other code knows which was used.
- **One key per machine, not per start.** `CREATE_NEW` semantics: if the file already exists it is read and its key
  adopted, so credentials encrypted yesterday still decrypt today, and two instances starting together end up with one
  key instead of one of them orphaning the other's ciphertexts. An empty file counts as "no key yet" and is filled.
- **Best-effort owner-only permissions** (`OWNER_READ`/`OWNER_WRITE`) where the filesystem is POSIX; on Windows the
  home directory's ACL is the protection.
- **A malformed value still fails at startup**, from either source — the key-file path does not soften ADR 0038's rule
  that a wrong key must fail loudly rather than at the first call that needs to decrypt something.
- **The refusal message names both ways out** (`BCOSMOS_AI_SECRET_KEY`, or a key file plus generation), because the
  409 can now only be reached by a deployment that set neither.
- The log records the **path** that was generated, never the key.

## Consequences

**Positive**

- Pasting a key in Settings works on a fresh checkout: start the backend, open Settings, paste. No shell step, no run
  configuration edit, no restart.
- Deployments are unaffected in either direction: setting `BCOSMOS_AI_SECRET_KEY` overrides the file, and a profile
  that does not enable generation behaves exactly as ADR 0038 described.
- The property is testable without a Spring context and without a home directory: `MasterKeySourceTest` runs the whole
  chain against a temp directory.

**Negative / accepted trade-offs**

- **A key on disk.** On a development machine the master key now sits in `~/.bcosmos/ai-secret.key` in the clear, so
  anyone who can read that file *and* the database can decrypt stored provider keys. That is a real reduction in
  protection compared with "environment only", accepted for the dev profile: the threat the cipher addresses there is
  the accidental leak (a dump, a backup, a log line), not a local attacker with file access, and the same machine
  normally holds the provider key in an IDE run configuration or a shell profile anyway.
- **Deviation from the letter of spec §10** ("credentials come from environment variables only"). The spec's own
  section 7.1 allows a secrets store, and the deviation is scoped to the default development profile; a deployment
  profile is unchanged. Flagged here rather than silently introduced (AGENTS.md rule 9).
- A second configuration knob (`secret-key-file`) that a reader has to know about before the behaviour makes sense.
  The startup log line names the file when a key is generated, and both properties are documented in
  `docs/ai-design.md` and `README.md`.
- Rotating the key means deleting the file and storing every pasted key again (or setting the variable instead) — the
  same property ADR 0038 already had, now reachable without a redeployment.

## Alternatives considered

- **Keep environment-only and improve the message** — rejected: the message was already precise; the problem was that
  the first feature use, not the startup, was where the operator learned about it, and that the fix required leaving
  the application.
- **Fail at startup when no master key is configured** — rejected: "no stored keys, providers read the environment"
  is a legitimate, working configuration (ADR 0038) and the corpus/pipeline features do not need a master key at all.
- **Generate a key and keep it only in memory** — rejected: the key would change on every restart, making every
  credential stored through the UI unreadable, which is worse than refusing to store one.
- **A separate "development secrets" service or a keystore file** — rejected as too much machinery for the same
  outcome; a 64-character file in the home directory is inspectable, deletable and already understood.
- **Encrypt the key file with DPAPI / the OS keychain** — noted as the next step if the dev file ever becomes a real
  concern; it would be a new implementation of `SecretCipher`'s key source, not a change to the registry, the API or
  the screens.
