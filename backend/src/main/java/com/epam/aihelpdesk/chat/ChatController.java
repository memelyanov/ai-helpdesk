package com.epam.aihelpdesk.chat;

import com.epam.aihelpdesk.chat.dto.ChatRequest;
import com.epam.aihelpdesk.chat.dto.ChatResponse;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code /chat} resource — a new bounded context distinct from {@code /documents} (research
 * Decision 1), not a verb added to {@code DocumentController}. Request-level validation
 * (FR-011 blank, FR-012 over-length) runs here, before {@link ChatService} is ever called;
 * FR-016's malformed-body/malformed-{@code documentIds} validation runs earlier still, inside
 * Jackson deserialization, and is mapped by {@link ChatErrorHandler} — so a single request can only
 * ever land in one of FR-007/FR-011/FR-012/FR-013/FR-016's outcomes, never two (FR-011).
 *
 * <p>Both the grounded-answer (FR-006) and "not covered" (FR-007) outcomes share the same
 * {@code 200 OK} status and {@link ChatResponse} shape (research Decision 7) — this method never
 * inspects which one {@link ChatService} returned before responding.
 *
 * <p>Feature 009: every request gets a correlation id in SLF4J's {@code MDC} under the key
 * {@code chatRequestId}, generated before validation so the id exists for the full lifetime of the
 * request, and always cleared in a {@code finally} block so it can never leak onto a later request
 * handled by the same worker thread (spec.md FR-008, research Decision 1). This correlates every
 * {@link ChatService} pipeline-stage log line (FR-001–FR-006), which all execute inside this
 * {@code try} block. It does not reach {@link ChatErrorHandler}'s own log lines: Java's
 * {@code finally} runs during exception unwinding, before Spring's {@code @RestControllerAdvice}
 * dispatch resolves the exception, so the id is already cleared by the time that separate,
 * pre-existing (feature 007) rejection/failure log line is written — consistent with those lines
 * being explicitly out of FR-008's scope (spec.md Edge Cases).
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final String MDC_CORRELATION_ID_KEY = "chatRequestId";

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        MDC.put(MDC_CORRELATION_ID_KEY, UUID.randomUUID().toString());
        try {
            String question = validate(request);
            ChatResponse response = chatService.answer(
                    new ChatRequest(question, request.documentIds(), request.includeTrace()));
            return ResponseEntity.ok(response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID_KEY);
        }
    }

    private static String validate(ChatRequest request) {
        String question = request.question() == null ? "" : request.question().trim();
        if (question.isEmpty()) {
            throw new InvalidChatRequestException("blank_question", "Question must not be blank.");
        }
        if (question.length() > ChatService.MAX_QUESTION_LENGTH) {
            throw new InvalidChatRequestException("question_too_long",
                    "Question must not exceed " + ChatService.MAX_QUESTION_LENGTH + " characters.");
        }
        return question;
    }
}
