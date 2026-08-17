/**
 * research.md Decision 5: a closed, pre-written lookup table per flow — never the backend's own
 * `error` code or `message` string is rendered (FR-007/FR-011/FR-014/FR-017). Every table has an
 * explicit fallback arm so no response, however malformed or unrecognized, ever leaves the UI
 * without a message to show.
 *
 * Two flows deliberately map the *same* two backend codes (`provider_unconfigured`,
 * `processing_failed`) differently: chat ({@link mapChatError}) keeps them as two distinct
 * messages (FR-007); upload ({@link mapUploadError}) intentionally collapses them — together with a
 * network-level failure with no response at all — into one "service unavailable" string (FR-011),
 * since none of the three is something the uploading user can act on differently. See research.md
 * Decision 5's clarification for the rationale.
 */

const FALLBACK_MESSAGE = 'Something went wrong. Please try again.';

// ---------------------------------------------------------------------------------------------
// Chat (POST /chat) — 007-chat-endpoint/contracts/chat-api-contract.md
// ---------------------------------------------------------------------------------------------

const CHAT_ERROR_MESSAGES: Record<string, string> = {
  blank_question: 'Type a question before sending.',
  question_too_long: 'Your question is too long. Please shorten it to 1000 characters or fewer.',
  malformed_request:
    'Your question could not be sent due to a formatting problem. Please try again.',
  provider_unconfigured: 'The assistant is not available right now. Please try again later.',
  processing_failed: 'Something went wrong while getting your answer. Please try again.',
};

/**
 * Maps a failed `POST /chat` response's `error` code to a fixed, human-readable message (FR-007).
 * `code === null`/`undefined` covers a network-level failure with no response at all — the same
 * generic fallback as a response whose `error` code isn't one of the five documented above, since
 * neither case gives this feature anything more specific to say.
 */
export function mapChatError(code: string | null | undefined): string {
  if (code == null) {
    return FALLBACK_MESSAGE;
  }
  return CHAT_ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE;
}

// ---------------------------------------------------------------------------------------------
// Upload (POST /documents) — 004-document-ingestion-endpoint/contracts/ingestion-api-contract.md
// ---------------------------------------------------------------------------------------------

const UPLOAD_SERVICE_UNAVAILABLE_MESSAGE =
  'The upload service is temporarily unavailable. Please try again.';

const UPLOAD_ERROR_MESSAGES: Record<string, string> = {
  unsupported_type: 'Only .pdf and .txt files are supported.',
  invalid_file:
    'That file could not be uploaded — check that it is not empty and is under the size limit.',
  unparseable: 'That file could not be read. It may be corrupted or in an unsupported format.',
  // FR-011: these two service-side codes are indistinguishable to the uploading user, so both
  // deliberately share the same string as a network failure (see mapUploadError below).
  provider_unconfigured: UPLOAD_SERVICE_UNAVAILABLE_MESSAGE,
  processing_failed: UPLOAD_SERVICE_UNAVAILABLE_MESSAGE,
};

/**
 * Maps a failed `POST /documents` response's `error` code to a fixed, human-readable message
 * (FR-011). `code === null`/`undefined` covers a network-level failure partway through the upload
 * — FR-011 requires this to read identically to a clean `provider_unconfigured`/`processing_failed`
 * rejection, so it resolves to the same {@link UPLOAD_SERVICE_UNAVAILABLE_MESSAGE}. A response with
 * an `error` code that isn't one of the five documented above falls back to the fully generic
 * message instead (research.md Decision 5) — a rarer case than a plain network failure.
 */
export function mapUploadError(code: string | null | undefined): string {
  if (code == null) {
    return UPLOAD_SERVICE_UNAVAILABLE_MESSAGE;
  }
  return UPLOAD_ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE;
}

// ---------------------------------------------------------------------------------------------
// Download (GET /documents/{id}/content) — 005-document-listing-download/contracts/document-query-api-contract.md
// ---------------------------------------------------------------------------------------------

/** FR-014's "no longer available" message — the one case where retrying can never help. */
export const DOCUMENT_NOT_FOUND_MESSAGE =
  'This source is no longer available — the document has been deleted.';

/** FR-014's message for any download failure other than a confirmed 404. */
export const DOWNLOAD_FAILED_MESSAGE = 'The download failed. Please try again.';

/**
 * Maps a failed download's `error` code to a fixed message (FR-014). Only `document_not_found` is
 * distinguished — every other cause (any other status, or a network-level failure) reads as the
 * same retryable {@link DOWNLOAD_FAILED_MESSAGE}, since the contract documents no other code for
 * this endpoint.
 */
export function mapDownloadError(code: string | null | undefined): string {
  if (code === 'document_not_found') {
    return DOCUMENT_NOT_FOUND_MESSAGE;
  }
  return DOWNLOAD_FAILED_MESSAGE;
}

// ---------------------------------------------------------------------------------------------
// Delete (DELETE /documents/{id}) — 006-document-delete/contracts/document-delete-api-contract.md
// ---------------------------------------------------------------------------------------------

const DELETE_ERROR_MESSAGES: Record<string, string> = {
  deletion_failed: 'The document could not be deleted. Please try again.',
  document_not_found: 'This document was already removed.',
};

/**
 * Maps a failed `DELETE /documents/{id}` response's `error` code to a fixed message (FR-017).
 * `code === null`/`undefined` (a network-level failure) and any unrecognized code both fall back to
 * the fully generic message (research.md Decision 5).
 */
export function mapDeleteError(code: string | null | undefined): string {
  if (code == null) {
    return FALLBACK_MESSAGE;
  }
  return DELETE_ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE;
}
