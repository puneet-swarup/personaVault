/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures async execution for background tasks.
 *
 * <p>Currently used by {@link com.personavault.service.ExtractionService}
 * to run LLM extraction without blocking the upload response.
 *
 * <p>Thread pool sizing: 2 threads is sufficient for single-user.
 * Extraction calls Ollama (blocking HTTP) and takes 5-30s. Two concurrent
 * extractions (e.g., user uploads 2 files rapidly) is the max realistic load.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated executor for document extraction tasks.
     * Named "extractionExecutor" to match the @Async annotation in ExtractionService.
     */
    @Bean("extractionExecutor")
    public Executor extractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("extract-");
        executor.initialize();
        return executor;
    }
}
