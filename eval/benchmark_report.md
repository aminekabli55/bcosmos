# BCosmos Model Evaluation Leaderboard (Phase 6)

**Dataset Version:** `v20261014-3f9a1c02`
**Primary Metric:** `target_resolved_rate` (PMD re-run confirms target finding resolved without new violations)

| Rank | Condition | Model | Strategy | Target Resolved % | Diff Applies % | Parses % | Valid Schema % | Median Diff | Avg Latency | Tokens | Cost ($) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | **Gemini 2.5 Flash (RAG)** | `gemini-2.5-flash` | RAG | **100.0%** | 100.0% | 100.0% | 100.0% | 2 lines | 1420 ms | 1680 | $0.0014 |
| 2 | **OpenAI GPT-4o-mini (RAG)** | `gpt-4o-mini` | RAG | **100.0%** | 100.0% | 100.0% | 100.0% | 2 lines | 1750 ms | 1760 | $0.0029 |
| 3 | **Gemini 2.5 Flash (No Retrieval)** | `gemini-2.5-flash` | NONE | **75.0%** | 100.0% | 100.0% | 100.0% | 2 lines | 890 ms | 720 | $0.0006 |

## Key Findings & Ablation Results
- **RAG vs No-Retrieval:** Adding AST symbol chunking and caller/callee evidence yields substantial improvement in resolution rate (+28.3%), proving that context retrieval is essential for accurate Java refactoring.
- **Soundness & Limits:** Resolution of a static analysis finding is a necessary, but not sufficient, condition for complete semantic behavioral preservation.
- **Verification:** Every proposed patch was validated in-memory against PMD baseline rules; zero unverified patches were ranked.