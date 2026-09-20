/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for the streaming chat endpoint.
 *
 * @param question the user's natural-language question
 */
public record ChatRequest(
        @NotBlank(message = "Question cannot be blank")
                @Size(max = 2000, message = "Question too long (max 2000 chars)")
                String question) {}
