# ADR-023: Answer Intent Routing (Tier Escalation)

## Status
Proposed (2026-10-29)

## Context
ADR-016 defines a three-tier truth model and an escalation ladder, but something must decide which tier a given question hits. Today `ChatController` always builds a `QuestionAnswerAdvisor` over the vector store — every question is a Tier 1 RAG query, even when the answer is a single stored fact.

We need a deterministic, testable router that walks the ladder and degrades gracefully.

## Decision
Introduce `AnswerRouterService`, called by `ChatController` before invoking the LLM.

### Router output
`RoutingDecision { tier, reason, hints }` where `tier` ∈ {FACT, WIKI, RAG}.

### Classification strategy (ordered)
1. Rule-based fast path (no LLM):
   - Matches "when does X expire", "what is my premium", "policy number" → FACT
   - Matches "how has X changed", "history of", "timeline", "summarise my" → WIKI
   - Matches "what do I know about <entity>" → WIKI
   - No matches → escalate to step 2
2. Small-LLM classifier (temperature 0, structured output): given the question and a short list of known entity names + wiki page titles, return one of FACT / WIKI / RAG with a reason. Cheap, one-shot.
3. Default fallback → RAG. If the classifier is unavailable or uncertain, RAG is the safe choice (it degrades to today's behaviour).

### Execution per tier
- FACT: query `fact` table via `FactQueryService`; render templated answer + citation + confidence percentage (ADR-021). No generation needed for most exact-fact questions; a thin LLM pass only if the user asked conversationally.
- WIKI: load the relevant wiki page(s); pass the page markdown (small) to the LLM with instructions to answer only from the page and cite its footnotes.
- RAG: today's path — `QuestionAnswerAdvisor` with `topK`, similarity threshold, category filter.

### Escalation on empty result
If FACT returns no match, fall through to WIKI, then RAG. If WIKI page is missing, fall through to RAG. The router records the fallback in the response's citation object so the UI can show "answered from documents (no compiled page yet)".

### Answer caching (optional, v1)
For FACT answers, cache the rendered answer keyed by (entityId, factKey, current version) for the session; invalidate on fact change. No caching for WIKI/RAG in v1.

## Consequences
- Plus: Most questions never touch the vector store — instant, exact, cited
- Plus: Falls back gracefully to today's behaviour when the vault is empty
- Plus: Deterministic rules make the common cases testable without an LLM
- Plus: Uniform citation contract (ADR-016) means the UI does not branch by tier
- Minus: Two classifiers (rules + LLM) to maintain; mitigated by keeping rules small and LLM a fallback
- Minus: Misclassification risk; mitigated by fall-through escalation
- Minus: FACT answers need templating per fact type; mitigated by generic renderer with type-aware formatting

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Always RAG (today) | Ignores stored truth; slow; re-derives every time |
| Always try FACT first | Many questions are narrative; would return "not found" often |
| LLM-only routing | Non-deterministic; slower; harder to test |
| User manually picks source | Poor UX; user shouldn't need to know tiers |

## Related
ADR-016 (ladder), ADR-021 (confidence display), ADR-022 (wiki pages), ADR-025 (telemetry)
