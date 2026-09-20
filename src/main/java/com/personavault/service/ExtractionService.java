/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personavault.entity.Policy;
import com.personavault.repository.PolicyRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Extracts structured policy data from raw document text using the LLM.
 *
 * <p>Per ADR-007, this is the "structured extraction" half of the two-pronged
 * approach. The LLM reads the full parsed text and outputs JSON with specific
 * fields. The result is stored in the {@code policies} table with
 * {@code verified = false}.
 *
 * <p>Runs <b>asynchronously</b> (per ADR-011) — the upload response returns
 * immediately after vector storage. Extraction happens in the background.
 *
 * <p>Uses the same Ollama model as chat but with:
 * <ul>
 *   <li>Different system prompt (extraction, not conversation)</li>
 *   <li>Temperature 0 (deterministic — no creativity for data extraction)</li>
 *   <li>Non-streaming call (we need the full JSON, not tokens)</li>
 * </ul>
 *
 * @see "ADR-007: Structured Extraction Over Pure RAG"
 * @see "ADR-011: Async Extraction"
 */
@Slf4j
@Service
public class ExtractionService {

    private static final String EXTRACTION_SYSTEM_PROMPT =
            """
        You are a data extraction engine. Read the insurance policy document \
        provided by the user and output ONLY valid JSON with the following fields. \
        If a field is not present in the document, set it to null. \
        Do not add any text before or after the JSON. Do not use markdown.

        JSON schema:
        {
          "policyNumber": string or null,
          "insurerName": string or null,
          "policyType": "HEALTH" | "CAR" | "LIFE" | "HOME" | "TRAVEL" | "OTHER",
          "effectiveDate": "YYYY-MM-DD" or null,
          "expiryDate": "YYYY-MM-DD" or null,
          "annualPremium": number or null,
          "premiumFrequency": "ANNUAL" | "SEMI_ANNUAL" | "QUARTERLY" | "MONTHLY" or null
        }
        """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_TEXT_LENGTH = 8000;

    private final ChatClient chatClient;
    private final PolicyRepository policyRepository;
    private final Executor extractionExecutor;

    public ExtractionService(ChatClient chatClient, PolicyRepository policyRepository, Executor extractionExecutor) {
        this.chatClient = chatClient;
        this.policyRepository = policyRepository;
        this.extractionExecutor = extractionExecutor;
    }

    /**
     * Triggers async extraction for a newly ingested document.
     *
     * <p>Called by {@link IngestionService} after vector storage completes.
     * The upload response has already been sent to the client by this point.
     *
     * @param documentId the ID of the ingested document
     * @param rawText    the full parsed text from Tika
     */
    @Async("extractionExecutor")
    public void extractAsync(Long documentId, String rawText) {
        log.info("Starting extraction for document {}", documentId);
        try {
            String json = callLlm(rawText);
            Policy policy = parseAndBuild(documentId, json);
            if (policy != null) {
                policyRepository.save(policy);
                log.info("Extraction complete: {} ({})", policy.getPolicyNumber(), policy.getPolicyType());
            } else {
                log.info("Extraction produced no policy data for document {}", documentId);
            }
        } catch (Exception e) {
            log.error("Extraction failed for document {}: {}", documentId, e.getMessage(), e);
        }
    }

    /**
     * Calls the LLM with the extraction prompt and returns the raw JSON response.
     *
     * @param rawText the full document text
     * @return the LLM's JSON response (may be null or malformed)
     */
    private String callLlm(String rawText) {
        String truncated = rawText.length() > MAX_TEXT_LENGTH ? rawText.substring(0, MAX_TEXT_LENGTH) : rawText;

        return chatClient
                .prompt()
                .system(EXTRACTION_SYSTEM_PROMPT)
                .user("Document text:\n\n" + truncated)
                .options(OllamaOptions.builder().temperature(0.0).build())
                .call()
                .content();
    }

    /**
     * Parses the LLM's JSON response and builds a {@link Policy} entity.
     *
     * <p>Package-private for direct unit testing without mocking ChatClient.
     * The LLM call is tested separately via integration tests.
     *
     * @param documentId the source document ID
     * @param json       the raw JSON string from the LLM
     * @return the extracted Policy, or null if parsing failed or no meaningful data found
     */
    @Transactional
    Policy parseAndBuild(Long documentId, String json) {
        if (json == null || json.isBlank()) {
            log.warn("LLM returned empty response for document {}", documentId);
            return null;
        }

        String cleaned = stripCodeFences(json.trim());

        try {
            JsonNode root = OBJECT_MAPPER.readTree(cleaned);
            if (root == null || !root.isObject()) {
                log.warn("LLM response is not a JSON object for document {}", documentId);
                return null;
            }
            return buildPolicy(documentId, root);
        } catch (Exception e) {
            log.error("Failed to parse LLM JSON for document {}: {}", documentId, e.getMessage());
            log.debug("Raw LLM response: {}", json);
            return null;
        }
    }

    /**
     * Builds a Policy entity from the parsed JSON node.
     * Returns null if no meaningful data was extracted (no policyNumber AND no expiryDate).
     */
    private Policy buildPolicy(Long documentId, JsonNode root) {
        String policyNumber = textOrNull(root, "policyNumber");
        LocalDate expiryDate = dateOrNull(root, "expiryDate");

        if (policyNumber == null && expiryDate == null) {
            return null;
        }

        Policy policy = new Policy();
        policy.setPolicyNumber(policyNumber != null ? policyNumber : "UNKNOWN");
        policy.setInsurerName(textOrNull(root, "insurerName"));
        policy.setPolicyType(textOrDefault(root, "policyType", "OTHER"));
        policy.setEffectiveDate(dateOrNull(root, "effectiveDate"));
        policy.setExpiryDate(expiryDate != null ? expiryDate : LocalDate.now().plusYears(1));
        policy.setAnnualPremium(decimalOrNull(root, "annualPremium"));
        policy.setPremiumFrequency(textOrNull(root, "premiumFrequency"));
        policy.setDocumentId(documentId);
        policy.setExtractedAt(Instant.now());
        policy.setVerified(false);

        return policy;
    }

    private String textOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String val = node.asText().trim();
        return val.isEmpty() ? null : val;
    }

    private String textOrDefault(JsonNode root, String field, String defaultValue) {
        String val = textOrNull(root, field);
        return val != null ? val : defaultValue;
    }

    private LocalDate dateOrNull(JsonNode root, String field) {
        String val = textOrNull(root, field);
        if (val == null) {
            return null;
        }
        try {
            return LocalDate.parse(val, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            try {
                return LocalDate.parse(val, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (Exception e2) {
                log.warn("Unparseable date for field '{}': '{}'", field, val);
                return null;
            }
        }
    }

    private BigDecimal decimalOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText().replace(",", "").replace(" ", ""));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Strips markdown code fences (```json ... ```) if the LLM added them
     * despite instructions to output raw JSON only.
     */
    private String stripCodeFences(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }
}
