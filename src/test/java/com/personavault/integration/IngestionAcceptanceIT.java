/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end acceptance test for the ingestion + Q&A pipeline.
 *
 * <p>Verifies the full stack works together:
 * <ol>
 *   <li>PostgreSQL + PGVector accepts connections and stores vectors</li>
 *   <li>Tika parses a real file</li>
 *   <li>Ollama embeds chunks and generates responses</li>
 *   <li>RAG retrieves grounded context from the vector store</li>
 * </ol>
 *
 * <p><b>Prerequisites (must be running before this test):</b>
 * <ul>
 *   <li>PostgreSQL 16 with pgvector: {@code docker compose up -d}</li>
 *   <li>Ollama with models: {@code ollama pull llama3:8b && ollama pull nomic-embed-text}</li>
 * </ul>
 *
 * <p><b>Run manually:</b>
 * <pre>
 *   mvn verify -Dtest=none -Dit.test=IngestionAcceptanceIT -Dfailsafe.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Excluded from normal CI runs via {@code @Disabled}. Enable by removing the annotation
 * when you want to run the full acceptance suite before a release tag.
 */
@Disabled("Requires PostgreSQL + Ollama running locally. Run manually before release.")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Ingestion + Q&A Acceptance Test")
class IngestionAcceptanceIT {

    @Autowired
    private TestRestTemplate rest;

    /** Path to a small sample text file bundled with the test resources. */
    private static final Path SAMPLE_FILE = Path.of("src/test/resources/sample_health_policy.txt");

    // --- Test 1: Upload ---

    @Test
    @Order(1)
    @DisplayName("should upload a document and return 201 with chunk count > 0")
    void uploadDocument() throws IOException {
        assertThat(Files.exists(SAMPLE_FILE))
                .as("Sample file must exist at: %s", SAMPLE_FILE.toAbsolutePath())
                .isTrue();

        FileSystemResource fileResource = new FileSystemResource(SAMPLE_FILE);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = rest.postForEntity("/api/documents/upload", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("sample_health_policy.txt");
        assertThat(response.getBody()).contains("\"chunkCount\":");
    }

    // --- Test 2: Document appears in list ---

    @Test
    @Order(2)
    @DisplayName("should list the uploaded document")
    void documentAppearsInList() {
        ResponseEntity<String> response = rest.getForEntity("/api/documents", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("sample_health_policy.txt");
    }

    // --- Test 3: Q&A — grounded answer ---

    @Test
    @Order(3)
    @DisplayName("should return a grounded answer for a question about the uploaded document")
    void chatReturnsGroundedAnswer() {
        // Give the embedding pipeline a moment to finish indexing
        // (vectorStore.accept() is synchronous, but Ollama embedding can be slow)
        sleep(5000);

        Map<String, String> question = Map.of("question", "What is the policy number of the health insurance policy?");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(question, headers);

        ResponseEntity<String> response = rest.exchange("/api/chat/stream", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotEmpty();

        // The answer should reference the policy number from the sample file
        // (HP-2026-00123 in our sample)
        assertThat(response.getBody())
                .as("Response should contain the policy number from the document")
                .containsIgnoringCase("HP-2026-00123");
    }

    // --- Test 4: Q&A — negative (not in docs) ---

    @Test
    @Order(4)
    @DisplayName("should say 'I don't have this information' for questions not in documents")
    void chatHandlesUnknownQuestion() {
        Map<String, String> question = Map.of("question", "What is the capital of Australia?");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(question, headers);

        ResponseEntity<String> response = rest.exchange("/api/chat/stream", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // The system prompt instructs the LLM to say it doesn't have the info.
        // We check for common phrasings since the LLM may word it differently.
        String body = response.getBody().toLowerCase(Locale.ROOT);
        assertThat(body)
                .satisfiesAnyOf(
                        b -> assertThat(b).containsIgnoringCase("don't have"),
                        b -> assertThat(b).containsIgnoringCase("do not have"),
                        b -> assertThat(b).containsIgnoringCase("not in"),
                        b -> assertThat(b).containsIgnoringCase("not found"),
                        b -> assertThat(b).containsIgnoringCase("cannot find"),
                        b -> assertThat(b).containsIgnoringCase("no information"));
    }

    // --- Test 5: Health endpoint ---

    @Test
    @Order(5)
    @DisplayName("health endpoint should return 200")
    void healthCheck() {
        ResponseEntity<String> response = rest.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- Helper ---

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
