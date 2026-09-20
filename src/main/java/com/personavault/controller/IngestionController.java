/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.personavault.dto.UploadResponse;
import com.personavault.entity.Document;
import com.personavault.repository.DocumentRepository;
import com.personavault.service.IngestionService;

/**
 * REST controller for document ingestion and listing.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/documents/upload} — upload and ingest a single file</li>
 *   <li>{@code GET /api/documents} — list all active documents</li>
 *   <li>{@code GET /api/documents/{id}} — get a single document's metadata</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/documents")
public class IngestionController {

    private final IngestionService ingestionService;
    private final DocumentRepository documentRepository;

    public IngestionController(IngestionService ingestionService, DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    /**
     * Uploads and ingests a single document.
     *
     * @param file the uploaded file (multipart/form-data)
     * @return 201 Created with document metadata, or 400 Bad Request on validation failure
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "OTHER") String category) {
        Document doc = ingestionService.ingest(file, category);
        UploadResponse response =
                new UploadResponse(doc.getId(), doc.getFileName(), doc.getChunkCount(), doc.getIngestedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all active (non-deleted) documents, most recent first.
     */
    @GetMapping
    public List<Document> listDocuments() {
        return documentRepository.findByDeletedAtIsNullOrderByIngestedAtDesc();
    }

    /**
     * Retrieves a single document's metadata by ID.
     *
     * @param id the document ID
     * @return the document entity, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable Long id) {
        return documentRepository
                .findById(id)
                .filter(doc -> !doc.isDeleted())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a document (soft-delete + vector removal + file removal).
     *
     * @param id the document ID
     * @return 204 No Content on success, 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        ingestionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
