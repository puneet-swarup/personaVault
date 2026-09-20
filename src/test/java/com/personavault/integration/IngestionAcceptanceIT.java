package com.personavault.integration;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Disabled("Manual integration test — requires Docker + Ollama models (~5GB)")
class IngestionAcceptanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("personavault")
            .withUsername("personavault")
            .withPassword("personavault");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void uploadAndQuery() {
        // 1. Upload a sample PDF (from src/test/resources/)
        MockHttpEntity<MultiValueMap<String, Object>> upload = buildUpload("sample_policy.pdf");
        ResponseEntity<String> uploadRes = rest.postForEntity("/api/documents/upload", upload, String.class);
        assertThat(uploadRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Wait for embedding to complete (poll document list)
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            ResponseEntity<String> list = rest.getForEntity("/api/documents", String.class);
            return list.getBody() != null && list.getBody().contains("sample_policy.pdf");
        });

        // 3. Ask a question
        Map<String, String> question = Map.of("question", "When does the policy renew?");
        ResponseEntity<String> chatRes = rest.postForEntity("/api/chat/stream", question, String.class);
        assertThat(chatRes.getBody()).isNotNull();
        assertThat(chatRes.getBody()).isNotEmpty();
    }
}
