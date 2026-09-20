# Acceptance Test — Phase 1

Run this after `docker compose up -d` + `ollama serve` + `mvn spring-boot:run`.

## Prerequisites
- PostgreSQL running: `docker compose up -d`
- Ollama running: `ollama serve`
- Models pulled: `ollama pull llama3:8b && ollama pull nomic-embed-text`
- App running: `./mvnw spring-boot:run`

## Test 1: Upload

1. Open http://localhost:8080 → login (`admin` / `admin`)
2. Go to **Documents**
3. Drag-drop a real PDF (e.g., a sample insurance policy)
4. **Expected:** Progress shows "✅ filename — N chunks"
5. **Expected:** Document appears in the table with correct name, type, size, chunk count

## Test 2: Q&A (grounded answer)

1. Go to **Chat**
2. Ask: "When does my [policy type] policy renew?"
3. **Expected:** Streaming response with a specific date
4. **Expected:** Response references the source document
5. **Expected:** Response time < 10 seconds

## Test 3: Q&A (negative — not in docs)

1. Ask: "What is the capital of France?"
2. **Expected:** "I don't have this information in your documents" or similar
3. **Expected:** Does NOT hallucinate an answer

## Test 4: Follow-up

1. After Test 2, ask: "What is the premium for that policy?"
2. **Expected:** Grounded answer referencing the same document

## Test 5: Restart resilience

1. Stop the app (Ctrl+C)
2. Restart: `./mvnw spring-boot:run`
3. Go to Documents → document list still shows the uploaded file
4. Go to Chat → ask the same question → still works (vectors persist in Postgres)

## Test 6: Offline

1. Disable network (or stop Ollama temporarily, then restart it)
2. Ask a question → works (no outbound calls)
3. Confirm in browser DevTools → Network tab: zero requests to external domains   