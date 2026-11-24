# ADR-023: Answer Intent Routing (Tier Escalation)

## Status
Accepted (2026-11-24) — increment 1 implemented: rule-based router, wiki tier execution,
citation frame in the chat stream. Deferred: the small-LLM classifier (step 2), direct
fact-table query with templated answers, and answer caching.

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

## Implementation notes (increment 1, 2026-11-24)

**What runs on every chat request**

| Step | Class | Cost |
|---|---|---|
| Classify intent | `AnswerRouterService` | regex over a normalised question; no I/O |
| Find the page | `WikiQueryService` | one `wiki_page` read, in-memory scoring, one file read |
| Answer | `ChatController` | one LLM call, prompt = the page |

**Rules are step 1 only.** Classification is ordered regex matching — exact-value phrasings
(expiry, premium, policy number, maturity, sum insured) → `FACT`; synthesis phrasings
("how has X changed", "when did X change", "history of", "timeline", "what do I know about",
"summarise") → `WIKI`; anything else → `RAG`. Deterministic and testable, so the common cases
need no model. The step-2 LLM classifier is deferred deliberately: RAG is a safe default while
vaults are small, and adding a model call to the router would make routing slower and less
predictable than the answers it is routing.

**Page retrieval is not vector search.** `WikiQueryService` reads the mirror rows once and
scores token overlap with IDF weighting — title ×3, slug ×2, body ×1, and a token found on
one page outweighs a token found on many (so "policy" counts for little, a policy number for
a lot). The page body then comes from the file on disk, because disk is canonical (ADR-022).

**Escalation is downward only.** A `FACT` intent that finds no page becomes `RAG`, with the
reason recorded ("no wiki page matched, escalated to document search"). Nothing ever
escalates from RAG up to the vault, so a misclassification can only cost an extra search,
never a wrong answer.

**Fact tier is the page, for now.** The page's `## Facts` table already carries the exact
value with its confidence and footnote, so an exact-value question is answered from the page
rather than from a templated fact query. Direct `fact` queries plus a type-aware renderer
remain open work.

**Citation contract.** Every answer sends one `citation` SSE frame before the first token —
`{"tier":"WIKI","slug":"...","title":"..."}` or `{"tier":"RAG","slug":null,"title":null}` —
so the UI shows provenance uniformly for both tiers.

## Related
ADR-016 (ladder), ADR-021 (confidence display), ADR-022 (wiki pages), ADR-025 (telemetry)
