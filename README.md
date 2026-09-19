# PersonaVault

**Local-first, single-user personal document assistant.**

Ask questions about your insurance policies, financial records, and medical history in plain English — get grounded, cited answers. Proactive alerts for renewals and deadlines.

> **100% offline. Nothing leaves your machine.**

---

## Why This Exists

I needed a way to query my own documents without sending sensitive data (insurance policies, medical reports, bank statements) to a cloud AI service. PersonaVault runs entirely on local hardware using a local LLM (via Ollama) and a local vector database (PostgreSQL + PGVector).

## Architecture at a Glance
Browser (Thymeleaf + vanilla JS + SSE)
│
▼
Spring Boot 3.4
├── ChatController → ChatClient + QuestionAnswerAdvisor → Ollama
├── IngestionController → Tika → TokenTextSplitter → VectorStore
├── RenewalCheckerService (@Scheduled, daily 8 AM)
├── DocumentController
├── DashboardController
└── NotificationController
│
├── PostgreSQL + PGVector (vectors + relational)
├── ./data/documents/ (original files)
└── Ollama (localhost:11434)


## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.4 |
| AI | Spring AI 1.0 + Ollama (Llama3-8B / Qwen2.5-14B) |
| Embeddings | nomic-embed-text (768-dim) |
| Vector + Relational DB | PostgreSQL 16 + PGVector |
| Document Parsing | Apache Tika + Tesseract OCR |
| Frontend | Thymeleaf + vanilla JS + SSE |
| Storage | Local filesystem |
| Security | Spring Security + AES-256-GCM field-level encryption |
| Build | Maven |

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 21+ | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| Docker + Compose | 24+ | https://docs.docker.com/get-docker/ |
| Ollama | latest | https://ollama.com/download |

## Quick Start

```bash
# 1. Clone
git clone https://github.com/puneet-swarup/personaVault.git
cd personaVault

# 2. Configure environment
cp .env.example .env
# Edit .env — set DB_PASSWORD at minimum

# 3. Pull LLM models (one-time, ~5 GB)
ollama pull llama3:8b
ollama pull nomic-embed-text

# 4. Start database
docker compose up -d

# 5. Run
./mvnw spring-boot:run   

Open http://localhost:8080

```

## Configuration

All configuration via environment variables (see .env.example):
| Variable | Default | Description |
|---|---|---|
|DB_USER | personavault | Postgres username |
|DB_PASSWORD | personavault | Postgres password |
|OLLAMA_CHAT_MODEL | llama3:8b | Ollama chat model name |

## Design Decisions
All major architectural decisions are documented as ADR (Architecture Decision Records). Each ADR captures the context, decision, consequences, and rejected alternatives.

| # | Decision |
|---|---|
| 001 |	Local LLM (Ollama) over cloud API |
| 002 |	PostgreSQL + PGVector over Chroma/Qdrant |
| 003 |	Thymeleaf + vanilla JS + SSE over React/HTMX |
| 004 |	Local filesystem over MinIO |
| 005 |	Spring AI over LangChain4j |
| 006 |	@Scheduled over db-scheduler (v1) |
| 007 |	Structured extraction over pure RAG for exact facts |
| 008 |	AES-256-GCM field-level encryption |