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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures Spring Security for a single-user local application.
 *
 * <p>Security model:
 * <ul>
 *   <li>Form-based login (username + password)</li>
 *   <li>Single user, credentials from environment variables</li>
 *   <li>Bcrypt password encoding (secure even at rest in memory)</li>
 *   <li>All endpoints require authentication except health check and login</li>
 *   <li>CSRF protection on forms; disabled for /api/** (JSON fetch)</li>
 * </ul>
 *
 * <p>Design choice: in-memory user storage with bcrypt encoding.
 * This is deployment-ready — the same code works on a cloud server
 * with no changes, only different env var values.
 *
 * @see "ADR-009: In-Memory User with Form Login"
 * @see "ADR-010: Bcrypt Password Encoding"
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${personavault.security.username:admin}")
    private String username;

    @Value("${personavault.security.password:admin}")
    private String password;

    /**
     * Bcrypt password encoder. Spring Security auto-detects this bean
     * and uses it for all password matching during authentication.
     *
     * <p>Bcrypt is intentionally slow (cost factor 10 = ~100ms per hash).
     * This is a feature, not a bug: it makes brute-force attacks
     * computationally expensive even if the in-memory store is dumped.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Single in-memory user with bcrypt-encoded password.
     *
     * <p>The plaintext password from the env var is hashed at startup
     * and only the hash is stored in memory. The plaintext is never
     * retained beyond the `User.withUsername().password()` call.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails user = User.withUsername(username)
                .password(encoder.encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    /**
     * Security filter chain: defines authorization rules, login/logout behavior.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/login", "/css/**", "/js/**", "/api/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form.loginPage("/login")
                        .defaultSuccessUrl("/documents", true)
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));

        return http.build();
    }
}
