/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.controller;

import java.io.IOException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.personavault.dto.ChatRequest;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for streaming chat (RAG Q&A).
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@code POST /api/chat/stream} — accepts a question, returns an SSE stream of tokens</li>
 * </ul>
 *
 * <p>The response is a Server-Sent Event stream. Each event contains a chunk
 * of the LLM's response. The client accumulates chunks to form the full answer.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client POSTs {@code { "question": "..." }}</li>
 *   <li>ChatClient embeds the question, retrieves top-K chunks from PGVector</li>
 *   <li>LLM generates a grounded answer token-by-token</li>
 *   <li>Each token is pushed to the client as an SSE event</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** Maximum time the SSE connection stays open (60 seconds). */
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final ChatClient chatClient;

    /**
     * Constructor injection.
     *
     * @param chatClient the RAG-enabled ChatClient (configured in AiConfig)
     */
    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Streams a grounded answer to the user's question.
     *
     * <p>Uses {@link SseEmitter} to bridge Spring AI's reactive {@code Flux<String>}
     * to the servlet-based SSE response. The Flux is subscribed to asynchronously;
     * each emitted token is sent as an SSE event.
     *
     * @param request the chat request containing the user's question
     * @return an SseEmitter that streams tokens until the LLM completes
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: {}", request.question());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        chatClient.prompt().user(request.question()).stream()
                .content()
                .subscribe(
                        // onNext: send each token as an SSE event
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (IOException e) {
                                log.debug("Client disconnected during stream", e);
                                emitter.completeWithError(e);
                            }
                        },
                        // onError: complete the stream with an error
                        error -> {
                            log.error("Chat stream error", error);
                            emitter.completeWithError(error);
                        },
                        // onComplete: signal end of stream to client
                        () -> {
                            log.debug("Chat stream completed");
                            emitter.complete();
                        });

        return emitter;
    }
}
