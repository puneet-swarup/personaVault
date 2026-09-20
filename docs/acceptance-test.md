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

## Phase 2 Tests

### Test 7: Structured Extraction
1. Upload `sample_health_policy.txt` with category "Health Insurance"
2. Wait 10-30s (async extraction)
3. **Expected:** `policies` table has a row with `policyNumber=HP-2025-00123`, `expiryDate=2026-03-14`, `verified=false`
4. Verify: `docker exec -it personavault-db psql -U personavault -d personavault -c "SELECT * FROM policies;"`

### Test 8: Category-Filtered Chat
1. Upload a health policy AND a car policy
2. In Chat, select category "Health Insurance"
3. Ask: "What is my policy number?"
4. **Expected:** Returns ONLY the health policy number (not car)

### Test 9: Delete Cascade
1. Upload a document → wait for extraction
2. Verify policy exists in DB
3. Click Delete → confirm
4. **Expected:** Document gone from list, policy row deleted, file gone from `./data/documents/`, vectors gone from `ai_vector_store`
5. Ask a question about the deleted doc → "I don't have this information"

### Test 10: Renewal Alert
1. Manually insert a policy with expiry in 20 days:
   ```sql
   INSERT INTO policies (policy_number, insurer_name, policy_type, expiry_date, annual_premium, document_id, verified)
   VALUES ('TEST-001', 'Test Insurer', 'HEALTH', CURRENT_DATE + 20, 15000, 1, true);   
2. Wait for next 8 AM (or restart app to trigger startup catch-up)
3. Expected: Notification in DB: SELECT * FROM notifications WHERE type='RENEWAL';
