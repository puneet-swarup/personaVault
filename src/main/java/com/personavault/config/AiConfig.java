/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Spring AI beans: text splitter and the RAG-enabled ChatClient.
 *
 * <p>Note: {@link org.springframework.ai.reader.tika.TikaDocumentReader} is NOT
 * configured as a bean here because it is stateful (per-resource). It is
 * instantiated per-file inside {@code IngestionService.parseDocument()}.
 */
@Configuration
public class AiConfig {

    private static final int CHUNK_SIZE = 500;
    private static final int MIN_CHUNK_SIZE_CHARS = 350;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 100;
    private static final int MAX_NUM_CHUNKS = 10_000;
    private static final boolean KEEP_SEPARATOR = true;

    private static final int RAG_TOP_K = 5;
    private static final double RAG_SIMILARITY_THRESHOLD = 0.50;

    private static final String SYSTEM_PROMPT =
            """
        You are a personal assistant with access to the user's documents \
        (insurance policies, financial records, medical history). \
        Only derive information from the provided context. \
        If the answer is not in the context, say so explicitly. \
        Never fabricate dates, amounts, or policy details. \
        Cite the source document when providing information.
        """;

    /**
     * Creates the token-based text splitter for chunking documents into
     * embedding-sized segments.
     *
     * <p>Parameters:
     * <ul>
     *   <li>chunkSize: target tokens per chunk (500)</li>
     *   <li>minChunkSizeChars: minimum characters before a split point is accepted (350)</li>
     *   <li>minChunkLengthToEmbed: minimum length to include a chunk (100)</li>
     *   <li>maxNumChunks: safety cap on chunks per document (10,000)</li>
     *   <li>keepSeparator: retain punctuation/newlines at chunk boundaries (true)</li>
     * </ul>
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(
                CHUNK_SIZE, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED, MAX_NUM_CHUNKS, KEEP_SEPARATOR);
    }

    /**
     * Creates the RAG-enabled ChatClient with the QuestionAnswerAdvisor.
     *
     * <p>Each chat call will:
     * <ol>
     *   <li>Embed the user's question</li>
     *   <li>Retrieve top-K similar chunks from PGVector</li>
     *   <li>Pass retrieved context to the LLM alongside the system prompt</li>
     *   <li>Stream the grounded response back</li>
     * </ol>
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder.defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder()
                                .topK(RAG_TOP_K)
                                .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                                .build())
                        .build())
                .build();
    }
}
