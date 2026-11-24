# ADR-015: Persistent Knowledge Vault Over Pure RAG

## Status
Proposed (2026-10-29) — Supersedes nothing; reframes ADR-007 as a special case.

## Context
The product's motto is *"hold correct data and hand it over directly — don't make the user search the haystack."* The current implementation is a classic RAG app: every chat question embeds the query, retrieves top-K chunks from PGVector, and asks the LLM to synthesise an answer at query time. Nothing accumulates. Ask a subtle cross-document question ("how has my health coverage changed since 2024?") and the LLM re-discovers the answer from scratch on every request — sometimes wrong, with no lasting record of the synthesis.

ADR-007 already introduced a mini version of the correct pattern for insurance: extract exact facts at ingest time into a `policies` table, use RAG only for interpretation. This ADR generalises that pattern to **all** document domains and adds a persistent synthesis layer.

## Decision
Adopt a **persistent knowledge vault** architecture with three tiers (detailed in ADR-016):

1. **Sources** — immutable uploaded documents, chunks, vectors (evidence).
2. **Facts** — typed, structured, provenance-tracked values extracted at ingest time (canonical truth).
3. **Synthesis (wiki)** — interlinked markdown pages that narrate, cross-reference, and version the facts over time.

Query answering escalates through the tiers: answer from the highest tier that can answer; fall back to RAG only for genuinely open-ended or novel questions. **RAG becomes the last resort, not the default.**

The vault is domain-agnostic. Insurance is the first populated domain; medical, financial, identity, legal follow per ADR-027.

## Consequences
- ✅ Answers get *faster and more accurate* with every document uploaded (the compounding property)
- ✅ Exact facts (dates, amounts, IDs) never come from LLM narrative — they come from structured rows
- ✅ Cross-document synthesis is compiled once, not re-derived per query
- ✅ Preserves existing RAG as a safety net for open-ended questions
- ❌ Three pipelines to maintain (extraction, compilation, retrieval) instead of two
- ❌ Provenance, confidence, and contradiction review must be first-class, or the vault drifts (ADR-019…021, ADR-024)
- ❌ Ingest cost increases (async compilation runs per upload)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Keep pure RAG (status quo) | Doesn't satisfy "no haystack search"; accuracy doesn't compound; cross-doc synthesis unreliable |
| Pure structured extraction, no synthesis | Facts answer point questions but cannot narrate change over time; loses human-readable state |
| Retrieve-then-rerank improvements only | Still re-derives every query; still no accumulation |
| Cloud RAG (NotebookLM-style) | Violates local-first NFR (ADR-001) |

## Related
- ADR-007 (insurance instance, promoted to special case)
- ADR-016 (three-tier model & routing)
- ADR-017 (generic entity/fact model)
- ADR-022 (wiki-on-disk)
