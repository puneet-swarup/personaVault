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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.personavault.entity.Document;
import com.personavault.repository.DocumentRepository;

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
    private final Path storagePath;

    /**
     * Constructor injection for all dependencies.
     *
     * @param textSplitter       chunking strategy
     * @param vectorStore        vector database (PGVector)
     * @param documentRepository relational metadata store
     * @param storagePath        root directory for original files
     */
    public IngestionService(
            TokenTextSplitter textSplitter,
            VectorStore vectorStore,
            DocumentRepository documentRepository,
            Path storagePath) {
        this.textSplitter = textSplitter;
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.storagePath = storagePath;
    }

    /**
     * Ingests a single document: parses, chunks, embeds, and stores it.
     *
     * @param file the uploaded file
     * @return the persisted {@link Document} entity with metadata
     * @throws IngestionException if any stage of the pipeline fails
     */
    @Transactional
    public Document ingest(MultipartFile file) {
        validateFile(file);
        log.info("Starting ingestion: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        try {
            // Stage 1: Store original file on local filesystem
            Path storedFile = storeOriginalFile(file);

            // Stage 2: Parse text using Tika
            List<org.springframework.ai.document.Document> rawDocs = parseDocument(storedFile);

            // Stage 3: Split into chunks
            List<org.springframework.ai.document.Document> chunks = textSplitter.apply(rawDocs);

            // Stage 4: Enrich chunks with source metadata
            Instant now = Instant.now();
            String docRef = UUID.randomUUID().toString().substring(0, 8);
            enrichChunks(chunks, file.getOriginalFilename(), now, docRef);

            // Stage 5: Embed and store in vector DB
            vectorStore.accept(chunks);
            log.info("Stored {} chunks for: {}", chunks.size(), file.getOriginalFilename());

            // Stage 6: Record metadata in relational DB
            Document entity = buildEntity(file, storedFile, chunks.size(), now);
            return documentRepository.save(entity);

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
            String docRef) {
        for (org.springframework.ai.document.Document chunk : chunks) {
            chunk.getMetadata().put("source", sourceName);
            chunk.getMetadata().put("ingestedAt", ingestedAt.toString());
            chunk.getMetadata().put("documentRef", docRef);
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
    private Document buildEntity(MultipartFile file, Path storedFile, int chunkCount, Instant now) {
        Document entity = new Document();

        String name = file.getOriginalFilename();
        entity.setFileName((name != null && !name.isBlank()) ? name : "unknown");

        entity.setStoredPath(storedFile.toString());

        String type = file.getContentType();
        entity.setMimeType((type != null && !type.isBlank()) ? type : "application/octet-stream");

        entity.setFileSizeBytes(file.getSize());
        entity.setChunkCount(chunkCount);
        entity.setIngestedAt(now);
        return entity;
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
