/*
 * Copyright 2025 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the local filesystem storage location for original documents.
 *
 * <p>The storage directory is created at application startup if it does not exist.
 * All original files are stored under this root with a UUID prefix to avoid
 * filename collisions.
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    /**
     * Resolves and ensures the document storage directory exists.
     *
     * @param storagePath the configured path (default: ./data/documents)
     * @return the resolved, existing directory path
     */
    @Bean
    public Path documentStoragePath(@Value("${personavault.storage.path:./data/documents}") String storagePath) {
        Path resolved = Path.of(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(resolved);
            log.info("Document storage initialized at: {}", resolved);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create document storage directory: " + resolved, e);
        }
        return resolved;
    }
}
