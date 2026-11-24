/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Provides a Clock bean so time-dependent services (e.g. FactService) can be
// tested deterministically by injecting a fixed clock.
@Configuration
public class ClockConfig {

    // System clock in UTC. Tests override this with a fixed clock.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
