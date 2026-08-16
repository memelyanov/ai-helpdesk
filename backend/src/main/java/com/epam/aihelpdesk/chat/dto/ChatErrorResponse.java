package com.epam.aihelpdesk.chat.dto;

/**
 * The shared {@code {error, message}} error surface for {@code POST /chat} — same shape as
 * {@code com.epam.aihelpdesk.ingestion.dto.DocumentErrorResponse}, but a separate class scoped to
 * this feature (research Decision 8). {@code message} is informational only and MUST NOT be parsed
 * by a caller (FR-015: never a credential value) — the HTTP status and {@code error} code together
 * are the stable contract.
 *
 * <p>{@code error} is one of:
 *
 * <ul>
 *   <li>{@code blank_question} — {@code 400}, {@code question} is missing, empty, or all
 *       whitespace (FR-011).</li>
 *   <li>{@code question_too_long} — {@code 400}, {@code question} exceeds 1000 characters after
 *       trimming (FR-012).</li>
 *   <li>{@code malformed_request} — {@code 400}, the request body itself could not be parsed
 *       (FR-016).</li>
 *   <li>{@code provider_unconfigured} — {@code 503}, the Azure OpenAI configuration is incomplete;
 *       no network call was attempted (FR-013).</li>
 *   <li>{@code processing_failed} — {@code 503}, the embedding call, retrieval query, or chat
 *       completion call failed for an otherwise-valid question (FR-013).</li>
 * </ul>
 */
public record ChatErrorResponse(String error, String message) {
}
