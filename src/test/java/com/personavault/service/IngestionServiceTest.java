/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.personavault.entity.Document;
import com.personavault.repository.DocumentRepository;
import com.personavault.repository.NotificationRepository;
import com.personavault.repository.PolicyRepository;
import com.personavault.service.IngestionService.IngestionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IngestionService}.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Happy path uses a real {@code .txt} file in {@code @TempDir} so Tika
 *       actually parses it — proving the pipeline works end-to-end at unit level.</li>
 *   <li>{@link TokenTextSplitter} and {@link VectorStore} are mocked to isolate
 *       the orchestration logic from embedding infrastructure.</li>
 *   <li>Validation and error paths are tested with crafted inputs.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionService")
class IngestionServiceTest {

    @Mock
    private TokenTextSplitter textSplitter;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ExtractionService extractionService;

    @TempDir
    Path tempStorage;

    private IngestionService ingestionService;

    private MockMultipartFile validTextFile;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(
                textSplitter,
                vectorStore,
                documentRepository,
                policyRepository,
                notificationRepository,
                tempStorage,
                jdbcTemplate,
                extractionService);
        validTextFile = new MockMultipartFile(
                "file",
                "health_policy_2025.txt",
                "text/plain",
                ("This is a health insurance policy document. "
                                + "The policy number is HP-2025-00123. "
                                + "The annual premium is 15000 INR. "
                                + "The policy expires on 2026-03-15.")
                        .getBytes());
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("should ingest a valid text file and return document with correct metadata")
        void shouldIngestValidTextFile() throws Exception {
            // Arrange: mock the splitter to return two fixed chunks
            var chunk1 = new org.springframework.ai.document.Document("chunk one text");
            var chunk2 = new org.springframework.ai.document.Document("chunk two text");
            when(textSplitter.apply(anyList())).thenReturn(List.of(chunk1, chunk2));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                ReflectionTestUtils.setField(d, "id", 1L);
                return d;
            });

            // Act
            Document result = ingestionService.ingest(validTextFile, "HEALTH_INSURANCE");

            // Assert: entity metadata
            assertThat(result).isNotNull();
            assertThat(result.getFileName()).isEqualTo("health_policy_2025.txt");
            assertThat(result.getMimeType()).isEqualTo("text/plain");
            assertThat(result.getChunkCount()).isEqualTo(2);
            assertThat(result.getFileSizeBytes()).isEqualTo(validTextFile.getSize());
            assertThat(result.getIngestedAt()).isNotNull();
            assertThat(result.isDeleted()).isFalse();
            assertThat(result.getStoredPath())
                    .startsWith(tempStorage.toAbsolutePath().toString());

            // Assert: file was actually written to disk
            Path stored = Path.of(result.getStoredPath());
            assertThat(Files.exists(stored)).isTrue();
            assertThat(Files.readString(stored)).contains("health insurance policy");

            // Assert: vector store received the chunks
            verify(vectorStore).accept(anyList());
        }

        @Test
        @DisplayName("should stamp source metadata on all chunks before storing")
        void shouldEnrichChunksWithMetadata() throws Exception {
            // Arrange
            var chunk = new org.springframework.ai.document.Document("test chunk content");
            when(textSplitter.apply(anyList())).thenReturn(List.of(chunk));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ingestionService.ingest(validTextFile, "HEALTH_INSURANCE");

            // Assert: metadata was stamped
            assertThat(chunk.getMetadata()).containsKey("source");
            assertThat(chunk.getMetadata().get("source")).isEqualTo("health_policy_2025.txt");
            assertThat(chunk.getMetadata()).containsKey("ingestedAt");
            assertThat(chunk.getMetadata()).containsKey("documentRef");
        }

        @Test
        @DisplayName("parseDocument should extract text from a real file via Tika")
        void shouldParseRealFileWithTika() throws Exception {
            // Arrange: write a real text file
            Path testFile = tempStorage.resolve("sample.txt");
            Files.writeString(testFile, "Hello World. This is a test document for Tika parsing.");

            // Act
            List<org.springframework.ai.document.Document> docs = ingestionService.parseDocument(testFile);

            // Assert
            assertThat(docs).isNotEmpty();
            assertThat(docs.get(0).getText()).contains("Hello World");
            assertThat(docs.get(0).getText()).contains("Tika parsing");
        }
    }

    @Nested
    @DisplayName("Validation Failures")
    class ValidationFailures {

        @Test
        @DisplayName("should reject null file")
        void shouldRejectNullFile() {
            assertThatThrownBy(() -> ingestionService.ingest(null, "HEALTH_INSURANCE"))
                    .isInstanceOf(IngestionException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("should reject empty file")
        void shouldRejectEmptyFile() {
            MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

            assertThatThrownBy(() -> ingestionService.ingest(empty, "HEALTH_INSURANCE"))
                    .isInstanceOf(IngestionException.class)
                    .hasMessageContaining("empty");
        }

        @ParameterizedTest
        @ValueSource(strings = {"application/pdf", "text/plain", "application/msword"})
        @DisplayName("should reject file exceeding 50MB limit")
        void shouldRejectOversizedFile(String contentType) {
            // 51 MB byte array
            byte[] oversized = new byte[51 * 1024 * 1024];
            MockMultipartFile big = new MockMultipartFile("file", "big.pdf", contentType, oversized);

            assertThatThrownBy(() -> ingestionService.ingest(big, "HEALTH_INSURANCE"))
                    .isInstanceOf(IngestionException.class)
                    .hasMessageContaining("50MB");
        }
    }

    @Nested
    @DisplayName("Parse Failures")
    class ParseFailures {

        @Test
        @DisplayName("should throw IngestionException when file path is invalid")
        void shouldThrowOnInvalidPath() {
            Path nonExistent = tempStorage.resolve("does_not_exist.txt");

            assertThatThrownBy(() -> ingestionService.parseDocument(nonExistent))
                    .isInstanceOf(IngestionException.class)
                    .hasMessageContaining("Failed to parse");
        }
    }

    @Nested
    @DisplayName("Entity Construction")
    class EntityConstruction {

        @Test
        @DisplayName("should handle null original filename gracefully")
        void shouldHandleNullFilename() throws Exception {
            MockMultipartFile noName = new MockMultipartFile("file", null, "text/plain", "content".getBytes());
            when(textSplitter.apply(anyList())).thenReturn(List.of(new org.springframework.ai.document.Document("x")));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            Document result = ingestionService.ingest(noName, "HEALTH_INSURANCE");

            assertThat(result.getFileName()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should handle null content type gracefully")
        @SuppressWarnings("ConstantConditions")
        void shouldHandleNullContentType() throws Exception {
            MockMultipartFile noType = new MockMultipartFile("file", "doc.txt", null, "content".getBytes());
            when(textSplitter.apply(anyList())).thenReturn(List.of(new org.springframework.ai.document.Document("x")));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            Document result = ingestionService.ingest(noType, "HEALTH_INSURANCE");

            assertThat(result.getMimeType()).isEqualTo("application/octet-stream");
        }
    }

    @Test
    @DisplayName("should wrap IOException from file storage into IngestionException")
    void shouldWrapIOException() throws Exception {
        // Point storagePath at a regular file, not a directory — Files.copy will throw
        Path badPath = tempStorage.resolve("not_a_directory");
        Files.writeString(badPath, "I am a file, not a dir");

        IngestionService badService = new IngestionService(
                textSplitter,
                vectorStore,
                documentRepository,
                policyRepository,
                notificationRepository,
                badPath,
                jdbcTemplate,
                extractionService);

        assertThatThrownBy(() -> badService.ingest(validTextFile, "HEALTH_INSURANCE"))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("Failed to ingest");
    }

    @Nested
    @DisplayName("Document Deletion")
    class DocumentDeletion {

        @Test
        @DisplayName("delete should soft-delete document, remove vectors, policies, and file")
        void shouldCascadeDelete() throws Exception {
            // Arrange: create a real file in tempStorage
            Path testFile = tempStorage.resolve("a3f2b1c4_test.pdf");
            Files.writeString(testFile, "content");

            Document doc = new Document();
            doc.setFileName("test.pdf");
            doc.setStoredPath(testFile.toString());
            doc.setCategory("HEALTH_INSURANCE");
            org.springframework.test.util.ReflectionTestUtils.setField(doc, "id", 1L);
            when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
            when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            ingestionService.delete(1L);

            // Assert: file is gone
            assertThat(Files.exists(testFile)).isFalse();

            // Assert: document is soft-deleted
            assertThat(doc.isDeleted()).isTrue();
            assertThat(doc.getDeletedAt()).isNotNull();

            // Assert: repository save was called (soft-delete persisted)
            verify(documentRepository).save(doc);
        }

        @Test
        @DisplayName("delete should throw IngestionException for non-existent document")
        void shouldThrowWhenNotFound() {
            when(documentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ingestionService.delete(999L))
                    .isInstanceOf(IngestionException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("delete should throw for already-deleted document")
        void shouldThrowWhenAlreadyDeleted() {
            Document doc = new Document();
            doc.setFileName("old.pdf");
            doc.setDeletedAt(Instant.now());
            org.springframework.test.util.ReflectionTestUtils.setField(doc, "id", 5L);
            when(documentRepository.findById(5L)).thenReturn(Optional.of(doc));

            assertThatThrownBy(() -> ingestionService.delete(5L))
                    .isInstanceOf(IngestionException.class)
                    .hasMessageContaining("not found");
        }
    }
}
