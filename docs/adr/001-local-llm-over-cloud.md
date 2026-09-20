
# ADR-001: Local LLM (Ollama) Over Cloud API

## Status
Accepted (2026-09-19)

## Context
PersonaVault ingests highly sensitive personal documents — insurance policies, medical records, financial statements. Sending this data to a cloud LLM API (OpenAI, Anthropic, etc.) violates the core privacy guarantee. The user requires **zero outbound network calls** during normal operation.

## Decision
Use **Ollama** running on `localhost:11434` as the sole LLM runtime. No cloud API fallback, no hybrid mode.

## Consequences
- ✅ Zero data exfiltration risk — PII never leaves the machine
- ✅ Works fully offline after initial model download
- ✅ No API costs, no rate limits, no vendor lock-in
- ❌ Slower inference than cloud GPUs (mitigated: ≤14B model, acceptable <10s response)
- ❌ Lower quality than frontier models (mitigated: RAG grounding + structured DB for exact facts)
- ❌ User must install and manage Ollama (documented in README)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| OpenAI / Anthropic / Google API | Data leaves machine; violates core privacy requirement |
| Self-hosted vLLM / TGI | Overkill for single-user; Ollama is simpler, lighter, GPU-optional |
| Hybrid (local for PII, cloud for general Q&A) | Complexity; user explicitly rejected any cloud dependency |
| CPU-only inference (no Ollama) | No model management, no streaming, reinventing the wheel |   