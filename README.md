# PersonaVault

**Local-first, single-user personal document assistant.**

Ask questions about your insurance policies, financial records, and medical history in plain English — get grounded, cited answers. Proactive alerts for renewals and deadlines.

> **100% offline. Nothing leaves your machine.**

---

## Why This Exists

I needed a way to query my own documents without sending sensitive data (insurance policies, medical reports, bank statements) to a cloud AI service. PersonaVault runs entirely on local hardware using a local LLM (via Ollama) and a local vector database (PostgreSQL + PGVector).

## Architecture at a Glance
```
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
```


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

### Git Hooks (Quality Gates)

This repo uses versioned git hooks to prevent bad code from entering the history.

After cloning, run once:

```bash
git config core.hooksPath scripts/hooks   
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

## Phasing
| Phase	| Scope	| Status |
|----|----|----|
| 1	| Ingestion + Q&A (insurance & financial docs) |	🚧 In Progress |
| 2	| Structured extraction + renewal alerts + dashboard | ⬜ |
| 3	| Medical docs + document management + manual corrections |	⬜ |
| 4	| OCR hardening, email alerts, export/wipe, security hardening | ⬜ |

## Non-Functional Requirements
- 100% local — zero outbound network calls during normal operation
- Q&A response < 10 seconds
- 20-page PDF ingestion < 60 seconds
- Sensitive fields encrypted at rest (AES-256-GCM)
- Works offline (after initial model download)
- LLM ≤ 14B parameters
- Restartable — scheduled checks resume after reboot

## Project Structure
```
src/main/java/com/personavault/
├── PersonaVaultApplication.java
├── config/          ← Security, AI, scheduling config
├── controller/      ← REST + Thymeleaf controllers
├── service/         ← Ingestion, chat, renewal, notification
├── repository/      ← JPA repositories
├── entity/          ← JPA entities (Policy, Document, Notification)
└── dto/             ← Request/response records   
```

## Code Quality

The build enforces quality gates via `mvn verify`. All checks must pass before code is considered mergeable.

| Tool | Purpose | Command |
|---|---|---|
| **Spotless** (Palantir) | Code formatting, import ordering | `mvn spotless:apply` (auto-fix) / `mvn spotless:check` |
| **Checkstyle** | Style rules (Javadoc, naming, imports) | runs in `verify` |
| **PMD** | Code smells, unused code, best practices | runs in `verify` |
| **SpotBugs** | Bytecode-level bug detection | runs in `verify` |
| **JaCoCo** | Code coverage (≥80% line, ≥70% branch) | report at `target/site/jacoco/index.html` |
| **Maven Enforcer** | Require Java 21+, Maven 3.9+, no duplicate deps | runs in `validate` |

### Running the Quality Gate

```bash
# Full quality gate (format + style + static analysis + tests + coverage)
mvn verify

# Auto-format code before committing
mvn spotless:apply

# View coverage report in browser
open target/site/jacoco/index.html   # macOS
xdg-open target/site/jacoco/index.html  # Linux   
```

## CI/CD

| Workflow | Trigger | What it does |
|---|---|---|
| **CI** | Push to `main`, PR to `main` | `mvn verify` (full quality gate) + coverage report |
| **Release** | Tag push (`v*`) | Package JAR + create GitHub Release |


## License
All rights reserved. This project is published for educational and portfolio purposes.