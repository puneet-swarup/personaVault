package com.personavault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PersonaVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonaVaultApplication.class, args);
    }
}   