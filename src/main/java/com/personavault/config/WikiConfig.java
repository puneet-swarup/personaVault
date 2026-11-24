/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.personavault.service.WikiFileStore;
import com.personavault.service.WikiIndexBuilder;
import com.personavault.service.WikiMarkdownRenderer;
import com.personavault.service.WikiPageValidator;

/**
 * Wires the wiki compiler's collaborators (ADR-022).
 *
 * <p>The pieces that carry no Spring dependencies — renderer, validator, index builder —
 * are plain objects created here rather than annotated as components, so they stay
 * trivial to unit test without a container.
 */
@Configuration
public class WikiConfig {

    /**
     * Creates the markdown renderer.
     *
     * @return a stateless renderer
     */
    @Bean
    public WikiMarkdownRenderer wikiMarkdownRenderer() {
        return new WikiMarkdownRenderer();
    }

    /**
     * Creates the provenance validator.
     *
     * @return a stateless validator
     */
    @Bean
    public WikiPageValidator wikiPageValidator() {
        return new WikiPageValidator();
    }

    /**
     * Creates the index builder.
     *
     * @return a stateless builder
     */
    @Bean
    public WikiIndexBuilder wikiIndexBuilder() {
        return new WikiIndexBuilder();
    }

    /**
     * Creates the wiki file store rooted at the configured directory.
     *
     * @param wikiPath vault directory (default: ./data/wiki)
     * @return a store that has created its root directory
     */
    @Bean
    public WikiFileStore wikiFileStore(@Value("${personavault.wiki.path:./data/wiki}") String wikiPath) {
        return new WikiFileStore(Path.of(wikiPath));
    }
}
