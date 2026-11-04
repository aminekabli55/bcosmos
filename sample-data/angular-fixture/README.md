# angular-fixture — Phase 10 TypeScript analysis sample

A deliberately small TypeScript/Angular project used to verify Phase 10 TypeScript support
(register, analyze, extract symbols, detect ESLint baseline findings, record parse failures).
BCosmos never builds or runs anything inside this tree.

The rules that fire are the four in
`analysis-core/src/main/resources/eslint/bcosmos-baseline.properties`, described in
[docs/rule-catalog.md](../../docs/rule-catalog.md).

## Findings a run over this tree produces

| Rule | Where | Why |
|---|---|---|
| `no-console` | `BookService.getBooks`, `book.service.ts:7` | `console.log` statement used |
| `no-unused-vars` | `BookService.getBooks`, `book.service.ts:8` | `unusedToken` is declared and never read |
| `no-explicit-any` | `BookService.getBooks`, `book.service.ts:9` | `rawResponse` uses explicit `any` type |
| `no-empty` | `BookService.getBooks`, `book.service.ts:12` | empty `catch` block |

Numbers for a run over this tree: 4 files discovered, 3 parsed, 1 failed, **4 findings**, 12 symbols (verified
against the real API on 2026-10-15).

Plus one parse failure that is recorded, not hidden:

- `src/app/syntax-error.ts` has broken syntax (`constructor( {`). Tree-sitter flags syntax errors, and the run records it as a failed file while continuing.

## Support level

TypeScript support is currently **Level 2 (Symbols & AST Linting)**:
- Full parsing and line-accurate symbol extraction (classes, interfaces, methods, functions, variables)
- AST-level rule evaluation matching ESLint baseline
- AI explanations via structured gateway
- Full graph/call hierarchy is reserved for Java (Level 3).
