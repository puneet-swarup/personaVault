/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.controller;

import java.io.IOException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personavault.dto.ChatCitation;
import com.personavault.dto.ChatRequest;
import com.personavault.service.AnswerPlan;
import com.personavault.service.AnswerService;
import com.personavault.service.AnswerTier;
import com.personavault.service.WikiPageContext;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for streaming chat.
 *
 * <p>Endpoint: {@code POST /api/chat/stream} — accepts a question, returns an SSE stream.
 *
 * <p>Two SSE event types are emitted:
 * <ul>
 *   <li>{@code citation} — one JSON frame naming the tier (and page, for wiki answers),
 *       sent before the first token so the UI can show provenance immediately</li>
 *   <li>{@code message} — the default unnamed frames carrying answer tokens</li>
 * </ul>
 *
 * <p>Which tier answers is decided by {@link AnswerService} (ADR-016, ADR-023): a compiled
 * wiki page when one matches, otherwise vector search over the source documents. RAG is
 * the fallback, not the default.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** Maximum time the SSE connection stays open (180 seconds). */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    /** Retrieval settings for the RAG fallback. */
    private static final int RAG_TOP_K = 5;

    private static final double RAG_SIMILARITY_THRESHOLD = 0.5;

    /** Keeps the model inside the page and preserving the provenance footnotes. */
    private static final String WIKI_SYSTEM_PROMPT = """
        You answer strictly from the compiled wiki page supplied by the user. \
        The page was built from their own documents and every claim carries a footnote \
        marker such as [^f:12].

        Rules:
        - Use only the page content. If the answer is not on the page, say you do not have it.
        - When you state a value, keep its footnote marker exactly as written.
        - Prefer the value listed under "Facts"; use "History" only when the question is
          about how something changed over time.
        - Be brief. Never invent dates, amounts or policy numbers.
        """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final AnswerService answerService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     *
     * @param chatClient the RAG-enabled ChatClient (configured in AiConfig)
     * @param vectorStore the document vector store, used for the RAG fallback
     * @param answerService decides which tier answers and loads the page
     * @param objectMapper serialises the citation frame
     */
    public ChatController(
            ChatClient chatClient, VectorStore vectorStore, AnswerService answerService, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.answerService = answerService;
        this.objectMapper = objectMapper;
    }

    /**
     * Streams a grounded answer to the user's question.
     *
     * <p>Uses {@link SseEmitter} to bridge Spring AI's reactive {@code Flux<String>} to the
     * servlet-based SSE response. The Flux is subscribed to asynchronously; each emitted
     * token is sent as an SSE event.
     *
     * @param request the chat request containing the user's question
     * @return an SseEmitter that streams a citation frame and then answer tokens
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: {}", request.question());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            log.warn("Chat stream timed out after {}ms", SSE_TIMEOUT_MS);
            sendToken(emitter, "[Response timed out. Please try again.]");
            emitter.complete();
        });
        emitter.onCompletion(() -> log.debug("Chat stream completed"));

        AnswerPlan plan = answerService.plan(request.question());
        if (plan.answersFromWiki()) {
            streamFromWiki(plan, request.question(), emitter);
        } else {
            streamFromDocuments(request, emitter);
        }
        return emitter;
    }

    // Tier 3: the answer comes from one compiled page, so the prompt carries that page.
    private void streamFromWiki(AnswerPlan plan, String question, SseEmitter emitter) {
        WikiPageContext page = plan.wikiPage().orElseThrow();
        sendCitation(emitter, new ChatCitation(plan.decision().tier().name(), page.slug(), page.title()));

        String userPrompt = "Question: " + question + "\n\nWiki page: " + page.slug() + "\n\n" + page.markdown();
        chatClient.prompt().system(WIKI_SYSTEM_PROMPT).user(userPrompt).stream()
                .content()
                .subscribe(chunk -> sendToken(emitter, chunk), error -> failStream(emitter, error), emitter::complete);
    }

    // Tier 1: the fallback that existed before the vault — embed, retrieve, generate.
    private void streamFromDocuments(ChatRequest request, SseEmitter emitter) {
        sendCitation(emitter, new ChatCitation(AnswerTier.RAG.name(), null, null));

        SearchRequest.Builder searchBuilder =
                SearchRequest.builder().topK(RAG_TOP_K).similarityThreshold(RAG_SIMILARITY_THRESHOLD);
        if (request.category() != null && !request.category().isBlank()) {
            searchBuilder.filterExpression("category == '" + request.category() + "'");
        }
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchBuilder.build())
                .build();

        chatClient.prompt().user(request.question()).advisors(advisor).stream()
                .content()
                .subscribe(chunk -> sendToken(emitter, chunk), error -> failStream(emitter, error), emitter::complete);
    }

    private void sendCitation(SseEmitter emitter, ChatCitation citation) {
        try {
            emitter.send(SseEmitter.event().name("citation").data(objectMapper.writeValueAsString(citation)));
        } catch (IOException e) {
            log.debug("Could not send citation frame for tier {}", citation.tier(), e);
        }
    }

    private void sendToken(SseEmitter emitter, String chunk) {
        try {
            emitter.send(SseEmitter.event().data(chunk));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void failStream(SseEmitter emitter, Throwable error) {
        log.error("Chat stream error", error);
        emitter.completeWithError(error);
    }
}
