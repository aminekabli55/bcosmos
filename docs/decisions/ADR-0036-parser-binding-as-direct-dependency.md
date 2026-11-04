# ADR 0036 — A parser binding is a direct dependency of the app that instantiates the adapter

- **Status:** Accepted
- **Date:** 2026-10-15
- **Related:** [ADR 0012](ADR-0012-language-adapter.md) (language adapter pattern),
  [ADR 0016](ADR-0016-per-module-repositories-for-now.md) (no root pom),
  [ADR 0033](ADR-0033-typescript-adapter.md) (TypeScript adapter), spec v2 §3.1 and §12

## Context

`analysis-core` owns the TypeScript adapter and therefore declares the tree-sitter-ng bindings
(`io.github.bonede:tree-sitter:0.25.3`, `io.github.bonede:tree-sitter-typescript:0.23.2`). The applications,
however, are the ones that *name* the adapter: `AnalysisConfiguration` in `backend` and `WorkerConfiguration`
in `analysis-worker` each `new TypeScriptAdapter()` inside a `@Bean` method, and both only declared their
dependency on `analysis-core`.

That worked as long as the binding travelled transitively, and it failed the moment a classpath was assembled
from anything other than a fresh Maven resolution — a cached module model, an IDE library list from before the
second language existed, a hand-built classpath. The symptom is unrecognisable at first sight, because it
names a class nobody in the repository mentions:

```
UnsatisfiedDependencyException: ... 'languageAdapterRegistry' ... 'typeScriptAdapter' ...
Factory method 'typeScriptAdapter' threw exception with message: org/treesitter/TSLanguage
```

Two things make it worse than an ordinary missing jar. `TypeScriptAdapter` is verified by the JVM while its
methods are linked, so the failure lands on the *bean*, not on the first parse; and the whole downstream graph
(PatchValidator, SuggestionService, ModelCompareService) reports the same error, so the app never starts even
though no analysis was requested.

## Decision

**Every application that constructs a language adapter declares the parser binding that adapter links
against.** Both `backend/pom.xml` and `analysis-worker/pom.xml` now carry `io.github.bonede:tree-sitter` and
`io.github.bonede:tree-sitter-typescript` at compile scope, with the versions pinned per module (there is no
root pom, ADR 0016) and a comment pointing here — so the next person to see this class of error reads the
reason next to the dependency instead of rediscovering it.

`analysis-core` keeps its own declarations: the adapter must be constructible and testable inside the module
that defines it.

**The binding is not made optional.** `@ConditionalOnClass` on the adapter bean was rejected. A missing parser
must fail loudly at startup, in the spirit of the existing policies for rules with no matching reference and
for MINED repositories (ADR 0024) — a backend that silently stopped seeing TypeScript would be worse than one
that refuses to boot.

**A test guards the requirement at the level it actually fails.** `AnalysisConfigurationTypeScriptBindingTest`
boots `AnalysisConfiguration` through an `ApplicationContextRunner`, asserts both languages are registered, and
parses one real TypeScript source. Bean creation alone reproduces the production failure; the parse adds the
native library load, which is the part a pure classpath assertion would miss.

## Consequences

- A fresh Maven build, the packaged fat jar and every future IDE import resolve the binding for the module that
  needs it, instead of depending on another module's transitive graph staying complete forever.
- The versions now exist in three poms. That is the price of ADR 0016's per-module repositories; the comments in
  all three say which versions must move together (they are the versions the adapter was tested against).
- A tool that cached its own classpath before this change still has to be re-imported. The pom states the
  requirement; only the owning tool can rebuild its model.
- The next adapter (Go, Python) inherits the pattern: declare the binding where the bean is built, and add the
  contract test rather than the native binding discovery to a shared helper.
