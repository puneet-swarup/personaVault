/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.personavault.entity.Document;
import com.personavault.repository.DocumentRepository;
import com.personavault.service.IngestionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link IngestionController} using MockMvc.
 *
 * <p>Tests the HTTP contract: status codes, JSON structure, and error handling.
 */
@WebMvcTest(IngestionController.class)
@WithMockUser
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("IngestionController")
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @MockitoBean
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("POST /api/documents/upload returns 201 with metadata on success")
    void uploadReturnsCreated() throws Exception {
        Document doc = new Document();
        ReflectionTestUtils.setField(doc, "id", 1L);
        doc.setFileName("policy.pdf");
        doc.setChunkCount(3);
        doc.setIngestedAt(Instant.now());
        when(ingestionService.ingest(any())).thenReturn(doc);

        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fileName").value("policy.pdf"))
                .andExpect(jsonPath("$.chunkCount").value(3))
                .andExpect(jsonPath("$.ingestedAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/documents/upload returns 400 on ingestion failure")
    void uploadReturnsBadRequestOnFailure() throws Exception {
        when(ingestionService.ingest(any()))
                .thenThrow(new IngestionService.IngestionException("File exceeds 50MB limit"));

        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[100]);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("File exceeds 50MB limit"));
    }

    @Test
    @DisplayName("GET /api/documents returns list of active documents")
    void listReturnsDocuments() throws Exception {
        Document doc = new Document();
        ReflectionTestUtils.setField(doc, "id", 1L);
        doc.setFileName("policy.pdf");
        when(documentRepository.findByDeletedAtIsNullOrderByIngestedAtDesc()).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fileName").value("policy.pdf"));
    }

    @Test
    @DisplayName("GET /api/documents/{id} returns 404 for non-existent document")
    void getReturns404WhenNotFound() throws Exception {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/999")).andExpect(status().isNotFound());
    }
}
