/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf page controller for the Chat view.
 *
 * <p>Serves the chat HTML page. The streaming Q&A is handled by
 * {@link ChatController} via SSE.
 */
@Controller
public class ChatPageController {

    /**
     * Serves the chat page.
     *
     * @return the Thymeleaf template name
     */
    @GetMapping("/chat")
    public String chatPage() {
        return "chat";
    }
}
