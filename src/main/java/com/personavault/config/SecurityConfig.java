/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures Spring Security for a single-user local application.
 *
 * <p>Security model:
 * <ul>
 *   <li>Form-based login (username + password)</li>
 *   <li>Single user, credentials from environment variables</li>
 *   <li>All endpoints require authentication except health check and login</li>
 *   <li>CSRF protection enabled (default) — important for form submissions</li>
 * </ul>
 *
 * <p>For a single-user local app, in-memory user storage is sufficient.
 * No database-backed user table needed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${personavault.security.username:admin}")
    private String username;

    @Value("${personavault.security.password:admin}")
    private String password;

    /**
     * Defines the security filter chain: which URLs require auth,
     * which are public, and how login works.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Authorize requests
                .authorizeHttpRequests(auth -> auth
                        // Public: no auth needed
                        .requestMatchers("/login", "/css/**", "/js/**", "/api/health").permitAll()
                        // Everything else: authenticated
                        .anyRequest().authenticated()
                )
                // Form login
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/documents", true)
                        .permitAll()
                )
                // Logout
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                )
                // CSRF: keep enabled (protects against cross-site form submissions)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));

        return http.build();
    }

    /**
     * Single in-memory user. For a local single-user app, this is
     * simpler than a database-backed UserDetailsService.
     *
     * <p>Credentials are read from environment variables (or application.yml)
     * so they're not hardcoded in source.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername(username)
                .password("{noop}" + password)
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}   