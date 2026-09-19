/*
 * Copyright 2025 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.dto;

import java.time.Instant;

/**
 * Response DTO returned after a successful document upload and ingestion.
 *
 * @param id         the assigned document ID
 * @param fileName   the original filename
 * @param chunkCount number of chunks stored in the vector database
 * @param ingestedAt timestamp when ingestion completed
 */
public record UploadResponse(Long id, String fileName, int chunkCount, Instant ingestedAt) {}
