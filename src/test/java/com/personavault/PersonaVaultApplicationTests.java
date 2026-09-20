/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full integration smoke test. Verifies the entire Spring context loads
 * with all beans wired (datasource, Flyway, Ollama, PGVector).
 *
 * <p>Requires: PostgreSQL running (docker compose up -d) and Ollama running.
 * Run manually: {@code mvn test -Dtest=PersonaVaultApplicationTests}
 */
@Disabled("Integration test — requires PostgreSQL and Ollama to be running locally")
@SpringBootTest
@DisplayName("Application Context (Integration)")
class PersonaVaultApplicationTests {

    @Test
    @DisplayName("context loads without errors")
    void contextLoads() {}
}
