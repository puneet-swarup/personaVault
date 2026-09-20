# ADR-007: Structured Extraction (Regex + LLM JSON) Over Pure RAG for Dates/Amounts

## Status
Accepted (2026-09-19)

## Context
Critical facts — policy expiry dates, premium amounts, policy numbers — must be **exact**. LLMs can hallucinate or misread numbers, especially from PDFs with complex layouts, tables, or mixed languages. RAG retrieves relevant text chunks but doesn't guarantee exact numeric extraction. Renewal alerts need to query "all policies expiring in the next 60 days" — a relational query, not a semantic search.

## Decision
Use a **two-pronged approach**:
1. **Structured extraction** (regex pre-pass + LLM JSON output) populates a relational `policies` table at ingestion time.
2. **RAG** (vector search + LLM) handles natural-language Q&A over the full document text.

The `policies` table is the **source of truth** for exact facts (dates, amounts, IDs). The LLM is only used for summarisation and natural-language interpretation. Renewal alerts query the DB directly — no LLM involved.

## Consequences
- ✅ Exact dates/amounts come from the DB, never from LLM memory
- ✅ Renewal alerts are deterministic SQL queries — no hallucination risk
- ✅ User can manually correct fields in the UI (Phase 3)
- ✅ "When does my health policy renew?" → DB lookup → exact answer
- ❌ Extraction may miss fields on unusual layouts (mitigated: manual correction UI)
- ❌ Two pipelines to maintain (extraction + RAG)
- ❌ LLM JSON extraction can still be wrong (mitigated: regex pre-pass + validation + manual review)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Pure RAG (ask LLM for dates) | Hallucination risk on numbers; can't do "all policies expiring in 60 days" as a query |
| Pure regex | Brittle; can't handle 20+ insurer layouts; misses context |
| Per-insurer template matching | Requires maintaining a template per insurer; not scalable |
| OCR + layout analysis (e.g., DocTR) | Heavy; overkill; same hallucination problem for semantic fields |   