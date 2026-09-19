# ADR-005: Spring AI Over LangChain4j

## Status
Accepted (2025-09-19)

## Context
We need: RAG (retrieval-augmented generation), chat model integration, vector store integration, document parsing (Tika), and text splitting. Two mature Java options: **Spring AI** and **LangChain4j**.

## Decision
Use **Spring AI 1.0** as the AI framework.

## Consequences
- ✅ First-class Spring Boot auto-configuration (zero boilerplate for Ollama, PGVector)
- ✅ `QuestionAnswerAdvisor` — RAG in one line of advisor config
- ✅ `ChatClient` fluent API with composable advisors
- ✅ `TikaDocumentReader` is a first-class Spring AI module
- ✅ Aligned with Spring Boot ecosystem (dependency management, actuator, security, profiles)
- ✅ `TokenTextSplitter` built-in
- ❌ Younger ecosystem (fewer community examples than LangChain4j)
- ❌ Tied to Spring (not a concern — we're a Spring Boot app)
- ❌ 1.0 is relatively new (stable release, but fewer production reports)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| LangChain4j | Good library, but more manual wiring; no Spring Boot auto-config; separate dependency management; no `QuestionAnswerAdvisor` equivalent as clean |
| Raw Ollama HTTP calls | No RAG abstraction; reinvent vector store, splitting, advisors, document parsing |
| Semantic Kernel (Microsoft) | .NET-first; Java port is secondary; less Spring integration |   