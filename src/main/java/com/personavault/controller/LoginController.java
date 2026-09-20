/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the login page.
 *
 * <p>The actual authentication is handled by Spring Security's
 * {@code UsernamePasswordAuthenticationFilter} — this controller
 * only renders the form.
 */
@Controller
public class LoginController {

    /**
     * Serves the login form.
     *
     * @return the Thymeleaf template name
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
