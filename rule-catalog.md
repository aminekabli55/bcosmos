# Rule catalog (Java & TypeScript baselines)

The rules BCosmos runs on Java are listed in one file,
[`analysis-core/src/main/resources/pmd/bcosmos-baseline.properties`](../analysis-core/src/main/resources/pmd/bcosmos-baseline.properties).
The rules BCosmos runs on TypeScript are listed in
[`analysis-core/src/main/resources/eslint/bcosmos-baseline.properties`](../analysis-core/src/main/resources/eslint/bcosmos-baseline.properties).
The adapter resolves each entry against the configured rule set, and the sha256 of
that properties file is stored on every analysis run as `rulesetHash` — so editing the list changes the identity of a
run, and runs with different hashes are not comparable.

## Java baseline

| Rule | PMD reference | Fires on |
|---|---|---|
| `EmptyCatchBlock` | `category/java/errorprone.xml/EmptyCatchBlock` | a `catch` block whose body does nothing |
| `UnusedPrivateField` | `category/java/bestpractices.xml/UnusedPrivateField` | a private field that is never read |
| `UnusedPrivateMethod` | `category/java/bestpractices.xml/UnusedPrivateMethod` | a private method that is never called |
| `AvoidCatchingGenericException` | `category/java/errorprone.xml/AvoidCatchingGenericException` | a `catch` of `Exception`, `RuntimeException`, `Throwable` or `Error` |

## TypeScript baseline (Phase 10)

| Rule | ESLint reference | Fires on |
|---|---|---|
| `no-empty` | `eslint/no-empty` | an empty block statement or empty catch block |
| `no-unused-vars` | `eslint/no-unused-vars` | a declared variable or parameter that is never read |
| `no-explicit-any` | `eslint/no-explicit-any` | an explicit `any` type annotation |
| `no-console` | `eslint/no-console` | calls to `console` logging methods |

The list is deliberately short: it is the measurable static baseline that later phases compare AI findings and
fix-triple extraction against, not a style guide. Extending it means editing the properties file and this table.

The rule itself is also described to the AI in
[`backend/src/main/resources/rules/rule-descriptions.properties`](../backend/src/main/resources/rules/rule-descriptions.properties):
retrieval puts that sentence in the prompt, and `RuleCatalogTest` fails if the two lists drift apart.

## Only rules that really fire belong here

A baseline rule that quietly produces nothing is coverage the catalog does not have. Two candidates were removed
for that reason, and `JavaAdapterAnalyzeTest.everyBaselineRuleFires` now pins that every listed rule reports on a
source that violates it:

- **`UnusedImports` was removed from PMD 7.** The rule no longer exists in `pmd-java` (it is in neither
  `codestyle.xml` nor `bestpractices.xml`), and PMD answers an *empty rule set* for the old reference instead of
  throwing. The adapter now treats a reference that resolves to no rule as a failure
  (`JavaAdapterAnalyzeTest.missingRuleReferenceIsRefusedLoudly`), so the two failure modes — a typo, and a rule PMD
  has dropped — are both loud rather than silently finding nothing.
- **`UnusedLocalVariable` does not fire on the simplest case under the pinned PMD 7.28.0.** A scope with exactly one
  unused local variable reports nothing; a scope with two reports them. That behaviour is not something a baseline
  can be measured against, so the rule is out until it is pinned by a test that shows it firing on one unused local.

## Two behaviours that are easy to get wrong

- **`EmptyCatchBlock` silently skips catch parameters named `ignore`, `ignored` or `expected`.** PMD's
  `allowExceptionNameRegex` defaults to those names, on the assumption that the author meant to swallow the
  exception. A fixture that expects this finding has to name the parameter something else: the passing sample in
  [EmptyCatchSample.java](../analysis-core/src/test/resources/fixtures/EmptyCatchSample.java) deviates from that,
  and the Phase 1 fixture under [`sample-data/java-fixture`](../sample-data/java-fixture/README.md) did not — its
  empty catch produced no finding until the parameter was renamed.
- **An entry that does not resolve fails the analysis.** A typo in the properties file, or a rule PMD has dropped,
  surfaces as a failed run with an error summary, never as a run that quietly reports fewer findings.
- **`AvoidCatchingGenericException` moved from `design.xml` to `errorprone.xml`.** The old path still resolves but PMD
  logs a deprecation warning on every analysis run and PMD 8 removes the alias, so the baseline uses the canonical
  reference.

## Severity

Findings keep two severities: the normalized `severity` the API filters on (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`,
`INFO`) and the tool's own `originalSeverity`, mapped by `PmdSeverityMapper` from the PMD priority. A finding is
never silently downgraded: the original value stays next to the normalized one.
