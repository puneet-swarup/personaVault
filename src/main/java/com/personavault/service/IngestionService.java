/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.UrlResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.personavault.entity.Document;
import com.personavault.repository.DocumentRepository;
import com.personavault.repository.NotificationRepository;
import com.personavault.repository.PolicyRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the document ingestion pipeline.
 *
 * <p>Pipeline stages:
 * <ol>
 *   <li><b>Store</b> — persist the original file to local filesystem with UUID prefix</li>
 *   <li><b>Parse</b> — extract text using Apache Tika (supports 1000+ formats)</li>
 *   <li><b>Split</b> — chunk text using {@link TokenTextSplitter} with overlap</li>
 *   <li><b>Enrich</b> — stamp metadata (source, ingestedAt, documentId) on each chunk</li>
 *   <li><b>Embed + Store</b> — embed chunks via Ollama and upsert to PGVector</li>
 *   <li><b>Record</b> — persist document metadata to the relational database</li>
 * </ol>
 */
@Slf4j
@Service
public class IngestionService {

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    private final TokenTextSplitter textSplitter;
    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final PolicyRepository policyRepository;
    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ExtractionService extractionService;
    private final Path storagePath;

    /**
     * Constructor injection for all dependencies.
     *
     * @param textSplitter       chunking strategy
     * @param vectorStore        vector database (PGVector)
     * @param documentRepository relational metadata store
     * @param policyRepository   policy metadata store
     * @param notificationRepository notification metadata store
     * @param storagePath        root directory for original files
     * @param jdbcTemplate       jdbcTemplate for vector CRUD
     * @param extractionService  extraction operations
     */
    public IngestionService(
            TokenTextSplitter textSplitter,
            VectorStore vectorStore,
            DocumentRepository documentRepository,
            PolicyRepository policyRepository,
            NotificationRepository notificationRepository,
            Path storagePath,
            JdbcTemplate jdbcTemplate,
            ExtractionService extractionService) {
        this.textSplitter = textSplitter;
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.policyRepository = policyRepository;
        this.notificationRepository = notificationRepository;
        this.storagePath = storagePath;
        this.jdbcTemplate = jdbcTemplate;
        this.extractionService = extractionService;
    }

    /**
     * Ingests a single document: parses, chunks, embeds, and stores it.
     *
     * @param file the uploaded file
     * @return the persisted {@link Document} entity with metadata
     * @throws IngestionException if any stage of the pipeline fails
     */
    @Transactional
    public Document ingest(MultipartFile file, String category) {
        validateFile(file);
        log.info(
                "Starting ingestion: {} ({} bytes, category={})", file.getOriginalFilename(), file.getSize(), category);

        try {
            Path storedFile = storeOriginalFile(file);
            List<org.springframework.ai.document.Document> rawDocs = parseDocument(storedFile);
            List<org.springframework.ai.document.Document> chunks = textSplitter.apply(rawDocs);

            Instant now = Instant.now();
            String docRef = UUID.randomUUID().toString().substring(0, 8);
            enrichChunks(chunks, file.getOriginalFilename(), now, docRef, category);

            vectorStore.accept(chunks);
            log.info("Stored {} chunks for: {}", chunks.size(), file.getOriginalFilename());

            Document entity = buildEntity(file, storedFile, chunks.size(), now, category);
            Document saved = documentRepository.save(entity);

            // Trigger async extraction (fire-and-forget)
            String rawText = rawDocs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .reduce("", (a, b) -> a + "\n" + b);
            extractionService.extractAsync(saved.getId(), rawText);

            return saved;

        } catch (IOException e) {
            throw new IngestionException("Failed to ingest document: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * Validates that the uploaded file meets ingestion requirements.
     *
     * @param file the file to validate
     * @throws IngestionException if file is null, empty, or exceeds size limit
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IngestionException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IngestionException("File exceeds 50MB limit: " + file.getOriginalFilename());
        }
    }

    /**
     * Persists the original file to the local filesystem with a UUID prefix
     * to avoid filename collisions.
     *
     * @param file the uploaded file
     * @return the resolved path where the file was stored
     * @throws IOException if the file cannot be written
     */
    private Path storeOriginalFile(MultipartFile file) throws IOException {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        file.getOriginalFilename();
        String originalName = file.getOriginalFilename();
        String storedName = uuid + "_" + originalName;
        Path target = storagePath.resolve(storedName);

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Stored file at: {}", target);
        return target;
    }

    /**
     * Parses the document using Tika, returning extracted text as Spring AI Documents.
     *
     * <p>{@link TikaDocumentReader} is instantiated per-resource (not a singleton)
     * because it holds state (parser, handler, metadata) for a single file.
     *
     * @param filePath path to the stored file
     * @return list of extracted documents (typically one per file)
     * @throws IngestionException if parsing fails
     */
    List<org.springframework.ai.document.Document> parseDocument(Path filePath) {
        try {
            UrlResource resource = new UrlResource(filePath.toUri());
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            return reader.get();
        } catch (Exception e) {
            throw new IngestionException("Failed to parse document: " + filePath.getFileName(), e);
        }
    }

    /**
     * Stamps source metadata onto each chunk for traceability in RAG responses.
     *
     * @param chunks       the list of chunks to enrich
     * @param sourceName   original filename
     * @param ingestedAt   ingestion timestamp
     * @param docRef       short unique reference for this document
     */
    private void enrichChunks(
            List<org.springframework.ai.document.Document> chunks,
            String sourceName,
            Instant ingestedAt,
            String docRef,
            String category) {
        for (org.springframework.ai.document.Document chunk : chunks) {
            chunk.getMetadata().put("source", sourceName);
            chunk.getMetadata().put("ingestedAt", ingestedAt.toString());
            chunk.getMetadata().put("documentRef", docRef);
            chunk.getMetadata().put("category", category);
        }
    }

    /**
     * Builds the JPA entity from the uploaded file and ingestion results.
     *
     * @param file       the original uploaded file
     * @param storedFile the path where the file was stored
     * @param chunkCount number of chunks sent to the vector store
     * @param now        ingestion timestamp
     * @return the unsaved entity ready for persistence
     */
    private Document buildEntity(MultipartFile file, Path storedFile, int chunkCount, Instant now, String category) {
        Document entity = new Document();

        String name = file.getOriginalFilename();
        entity.setFileName((name != null && !name.isBlank()) ? name : "unknown");

        entity.setStoredPath(storedFile.toString());

        String type = file.getContentType();
        entity.setMimeType((type != null && !type.isBlank()) ? type : "application/octet-stream");

        entity.setFileSizeBytes(file.getSize());
        entity.setChunkCount(chunkCount);
        entity.setIngestedAt(now);
        entity.setCategory(category != null && !category.isBlank() ? category : "OTHER");

        return entity;
    }

    @Transactional
    public void delete(Long id) {
        Document doc = documentRepository
                .findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new IngestionException("Document not found: " + id));

        // 1. Remove vectors
        deleteVectors(doc);

        // 2. Remove policies
        policyRepository.deleteByDocumentId(id);

        // 3. Remove notifications (via policies)
        policyRepository.findByDocumentId(id).forEach(p -> notificationRepository.deleteByPolicyId(p.getId()));

        // 4. Remove file
        deleteFile(doc.getStoredPath());

        // 5. Soft-delete
        doc.setDeletedAt(Instant.now());
        documentRepository.save(doc);

        log.info("Deleted document: {} (id={})", doc.getFileName(), id);
    }

    private void deleteVectors(Document doc) {
        Path path = Path.of(doc.getStoredPath());
        Path fileName = path.getFileName();
        if (fileName == null) {
            log.warn("Cannot determine documentRef for deletion: {}", doc.getStoredPath());
            return;
        }
        String docRef = fileName.toString().substring(0, 8);
        jdbcTemplate.update("DELETE FROM ai_vector_store WHERE metadata->>'documentRef' = ?", docRef);
    }

    private void deleteFile(String storedPath) {
        try {
            Files.deleteIfExists(Path.of(storedPath));
        } catch (IOException e) {
            log.warn("Could not delete file: {}", storedPath, e);
        }
    }

    /**
     * Runtime exception for ingestion pipeline failures.
     * Wraps checked exceptions to keep the service API clean.
     */
    public static class IngestionException extends RuntimeException {

        public IngestionException(String message) {
            super(message);
        }

        public IngestionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
