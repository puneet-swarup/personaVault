# ADR-016: Three-Tier Truth Model & Query Escalation Ladder

## Status
Proposed (2026-10-29)

## Context
ADR-015 establishes a persistent knowledge vault. For this to be usable, we need a precise model of where truth lives and how a query is answered, including which source wins when tiers disagree.

Today the chat path always goes to vector search. This makes "answer from stored truth" impossible because there is no defined order of authority.

## Decision

### The three tiers

| Tier | Name | Content | Authority |
|---|---|---|---|
| 3 | Synthesis | Interlinked markdown wiki pages (`./data/wiki/`) | Narrative / explanatory |
| 2 | Facts | Typed entities + facts table (ADR-017) | Canonical for exact values |
| 1 | Sources | Uploaded files, chunks, vectors | Evidence / fallback |

### Authority rule
- Tier 2 is authoritative for exact values (dates, amounts, IDs, names).
- Tier 3 is authoritative for narrative and cross-document synthesis.
- Tier 1 is the evidence of record. Everything in Tiers 2 and 3 must cite Tier 1.
- If Tier 2 and Tier 3 disagree, Tier 2 wins and the wiki page is flagged `needsReview`.

### Query escalation ladder

A router (ADR-023) classifies intent and walks the ladder:

1. Exact-fact intent → answer from Tier 2. Cite the fact and its source doc.
2. Synthesis / trend intent → answer from Tier 3 (wiki page). Cite the page and the facts it references.
3. Entity lookup ("what do I know about X?") → answer from Tier 3 entity page.
4. Open-ended / novel ("summarise the exclusions in doc Y") → Tier 1 RAG.

### Citation contract
Every answer carries a uniform citation object regardless of tier:
`{ tier, refs: [{ docId, page?, snippet?, factId?, pageSlug? }], confidence }`

The UI renders this the same way for all tiers.

### Confidence display
Per decision (2026-10-29): when a Tier-2 answer is presented, show an explicit confidence percentage alongside the source link (e.g. "24,000 (92% confidence - source: policy_hdfc_2026.pdf)"). Low-confidence facts are still answered but visibly hedged.

## Consequences
- Plus: "Answer from truth, fall back to search" is now a defined, testable rule
- Plus: Exactness and narrative are cleanly separated
- Plus: Uniform citation contract simplifies UI and evals
- Plus: Confidence percentage makes accuracy visible to the user
- Minus: Requires an intent router (ADR-023) to work well
- Minus: Requires facts and wiki to carry provenance (ADR-021) or the ladder is untrustworthy

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Always Tier 1 (today) | Reintroduces haystack search as the default |
| Always Tier 2 | Cannot answer narrative/trend questions; forces rigid schemas |
| Always Tier 3 | Wiki could drift from facts; no exact-value guarantee |
| LLM picks tier freely | Non-deterministic; violates "correct data" motto |

## Related
ADR-015 (umbrella), ADR-017 (fact model), ADR-021 (confidence), ADR-022 (wiki), ADR-023 (router)
