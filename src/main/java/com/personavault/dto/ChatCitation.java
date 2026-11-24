/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.dto;

/**
 * Citation metadata sent to the chat client as an SSE event (ADR-016 citation contract).
 *
 * <p>Every answer carries one of these regardless of which tier produced it, so the UI
 * renders provenance the same way for a stored fact, a compiled page and a document
 * search hit.
 *
 * @param tier the tier that answered: FACT, WIKI or RAG
 * @param slug the wiki page slug, null for document-search answers
 * @param title the wiki page title, null for document-search answers
 */
public record ChatCitation(String tier, String slug, String title) {}
