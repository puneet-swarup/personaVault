/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an ingested document's metadata.
 *
 * <p>The original file is stored on the local filesystem at {@link #storedPath}.
 * This entity tracks metadata only — the binary content lives outside the database.
 *
 * <p>Soft-delete is supported via {@link #deletedAt}: a non-null value indicates
 * the document has been logically removed but the row is retained for audit.
 */
@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /** Original filename as uploaded (e.g., "health_policy_2026.pdf"). */
    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    /** UUID-prefixed path on local filesystem (e.g., "./data/documents/a3f2_..._file.pdf"). */
    @Column(name = "stored_path", nullable = false, length = 1024)
    private String storedPath;

    /** MIME type detected at upload time. */
    @Column(name = "mime_type", nullable = false, length = 256)
    private String mimeType;

    /** File size in bytes. */
    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    /** Number of chunks sent to the vector store during ingestion. */
    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    /** Comma-separated user-assigned tags. */
    @Column(name = "tags", length = 1024)
    private String tags;

    /** Timestamp when ingestion completed. */
    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    /** Timestamp of soft-delete. Null means the document is active. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Primary domain classification for RAG narrowing. */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /**
     * Returns true if this document has been soft-deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
