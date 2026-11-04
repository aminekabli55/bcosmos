# ai-lab/prompts

This folder is for **experiment and evaluation prompts**: the variants Phase 6 compares (no retrieval vs RAG,
model A vs model B, prompt v1 vs prompt v2) and the notes that explain what changed between them.

The **runtime** prompts are not here. They live with the backend, because the backend must build, test and run
without this repository (each module is its own Git repository, ADR 0016):

```
backend/src/main/resources/prompts/<name>.<version>.md    # instructions, with {{placeholders}}
backend/src/main/resources/prompts/<name>.<version>.json  # the JSON Schema the answer must satisfy
```

`PromptTemplateLoader` reads them, `{{placeholder}}` values are filled by `PromptTemplate.render`, and the version
(for example `explain-finding.v1`) is stored on every `llm_usage` row so a stored answer can always be traced back to
the prompt that produced it. The decision and its reason are in
[ADR 0019](../../docs/decisions/ADR-0019-ai-gateway.md).

When an experiment here beats the shipped prompt, promote it by adding a new versioned file under
`backend/src/main/resources/prompts/` — never by editing `v1` in place, which would silently change the meaning of
past usage rows.
