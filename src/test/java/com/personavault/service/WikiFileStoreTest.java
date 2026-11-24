/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.personavault.entity.WikiPageType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WikiFileStore")
class WikiFileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should write a page under its type folder")
    void shouldWriteUnderTypeFolder() {
        WikiFileStore store = new WikiFileStore(tempDir.resolve("wiki"));

        Path written = store.write(WikiPageType.ENTITY, "policy-hp-1", "# HP-1\n");

        assertEquals(
                tempDir.resolve("wiki").resolve("entity").resolve("policy-hp-1.md"),
                written,
                "should write a page under its type folder");
        assertTrue(Files.isRegularFile(written), "should write a page under its type folder");
    }

    @Test
    @DisplayName("should read back what it wrote")
    void shouldReadBack() {
        WikiFileStore store = new WikiFileStore(tempDir);
        store.write(WikiPageType.ENTITY, "policy-hp-1", "# HP-1\n\nbody\n");

        String content = store.read(WikiPageType.ENTITY, "policy-hp-1").orElseThrow();

        assertTrue(content.contains("# HP-1"), content);
    }

    @Test
    @DisplayName("should return empty when a page has never been compiled")
    void shouldReturnEmptyForMissingPage() {
        WikiFileStore store = new WikiFileStore(tempDir);
        assertTrue(
                store.read(WikiPageType.ENTITY, "never-written").isEmpty(),
                "should return empty when a page has never been compiled");
    }

    @Test
    @DisplayName("should normalise CRLF to LF and add a trailing newline")
    void shouldNormaliseLineEndings() {
        WikiFileStore store = new WikiFileStore(tempDir);
        Path written = store.write(WikiPageType.ENTITY, "policy-hp-1", "# HP-1\r\nbody without newline");

        String content = read(written);

        assertFalse(content.contains("\r"), "content still has CR");
        assertTrue(content.endsWith("\n"), "content has no trailing newline");
        assertEquals(
                "# HP-1\nbody without newline\n", content, "should normalise CRLF to LF and add a trailing newline");
    }

    @Test
    @DisplayName("should write the index at the vault root")
    void shouldWriteIndexAtRoot() {
        WikiFileStore store = new WikiFileStore(tempDir.resolve("wiki"));

        Path written = store.writeIndex("---\nslug: index\n---\n");

        assertEquals(tempDir.resolve("wiki").resolve("index.md"), written, "should write the index at the vault root");
        assertTrue(Files.isRegularFile(written), "should write the index at the vault root");
    }

    @Test
    @DisplayName("should overwrite an existing page atomically")
    void shouldOverwrite() {
        WikiFileStore store = new WikiFileStore(tempDir);
        store.write(WikiPageType.ENTITY, "policy-hp-1", "first\n");
        store.write(WikiPageType.ENTITY, "policy-hp-1", "second\n");

        assertEquals(
                "second\n",
                store.read(WikiPageType.ENTITY, "policy-hp-1").orElseThrow(),
                "should overwrite an existing page atomically");
    }

    @Test
    @DisplayName("should leave no temp files behind")
    void shouldNotLeaveTempFiles() throws IOException {
        WikiFileStore store = new WikiFileStore(tempDir);
        store.write(WikiPageType.ENTITY, "policy-hp-1", "# HP-1\n");

        try (var files = Files.list(tempDir.resolve("entity"))) {
            assertEquals(1, files.count(), "should leave no temp files behind");
        }
    }

    @Test
    @DisplayName("should delete a page")
    void shouldDelete() {
        WikiFileStore store = new WikiFileStore(tempDir);
        store.write(WikiPageType.ENTITY, "policy-hp-1", "# HP-1\n");

        assertTrue(store.delete(WikiPageType.ENTITY, "policy-hp-1"), "should delete a page");
        assertFalse(store.delete(WikiPageType.ENTITY, "policy-hp-1"), "should delete a page");
    }

    @Test
    @DisplayName("should refuse a slug that could escape the vault directory")
    void shouldRefuseUnsafeSlug() {
        WikiFileStore store = new WikiFileStore(tempDir);

        assertThrows(IllegalArgumentException.class, () -> store.pathFor(WikiPageType.ENTITY, "../evil"));
        assertThrows(IllegalArgumentException.class, () -> store.pathFor(WikiPageType.ENTITY, "Bad Slug"));
        assertThrows(IllegalArgumentException.class, () -> store.pathFor(WikiPageType.ENTITY, null));
    }

    @Test
    @DisplayName("should create the vault root when it does not exist")
    void shouldCreateRoot() {
        Path root = tempDir.resolve("nested").resolve("wiki");
        new WikiFileStore(root);
        assertTrue(Files.isDirectory(root), "should create the vault root when it does not exist");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
