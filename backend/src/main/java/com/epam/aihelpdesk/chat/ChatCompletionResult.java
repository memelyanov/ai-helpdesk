package com.epam.aihelpdesk.chat;

/**
 * Returned by {@link ChatCompletionClient#complete(String, java.util.List)} in place of the bare
 * {@code String} it returned before this feature (research Decision 3) — never serialized directly;
 * {@link ChatService} reads it to build both the {@code prompt_assembled} and
 * {@code model_response_received} trace steps (when requested, FR-012) and its own summary-only log
 * lines (always, FR-017).
 *
 * @param systemPrompt the fixed constant {@link ChatCompletionClient#SYSTEM_PROMPT}, included for
 *                      completeness even though it never varies per request.
 * @param prompt       the exact {@code "Context:\n" + <passages> + "\n\nQuestion: " + question} text
 *                      sent to the model — never reconstructed a second time elsewhere.
 * @param completion   the raw completion text — possibly blank (unchanged "empty completion"
 *                      handling).
 */
public record ChatCompletionResult(String systemPrompt, String prompt, String completion) {
}
