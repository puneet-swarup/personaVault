/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf page controller for the Documents view.
 *
 * <p>Serves the HTML page. The actual data (document list, upload)
 * is handled by the REST API ({@link IngestionController}) via fetch calls
 * from the frontend JavaScript.
 *
 * <p>This separation (page controller vs. REST controller) follows the
 * "API + template" pattern: the server renders the shell, the client
 * fetches data dynamically.
 */
@Controller
public class DocumentsPageController {

    /**
     * Serves the documents page.
     *
     * @return the Thymeleaf template name
     */
    @GetMapping("/documents")
    public String documentsPage() {
        return "documents";
    }
}
