/**
 * Client-side chat types (data-model.md `ChatMessage`/`Citation`) and the pure
 * `ChatResponse.sources` → `Citation[]` mapping (FR-002, research.md Decision 7). No field is ever
 * invented, dropped, or reordered — this is the direct 1:1 mirror the plan.md Summary calls out as
 * what makes SC-002 true by construction.
 */

export type ChatRole = 'user' | 'assistant';
export type ChatMessageStatus = 'pending' | 'complete' | 'error';

/** One badge under an assistant message (FR-002, data-model.md `Citation`). */
export interface Citation {
  documentId: string;
  filename: string;
  /** Rendered exactly as received — a page number as a string, or "no page structure". */
  pageLabel: string;
  /** `Math.round(score * 100)`, always in [50, 100] (research.md Decision 7). */
  scorePercent: number;
  /** `false` only after a download attempt against this citation returns 404 (FR-014). */
  available: boolean;
}

/**
 * One recorded pipeline stage, exactly as `POST /chat` returns it — a direct structural mirror of
 * 009's own `ChatTraceStep` (010-chat-trace-dialog/data-model.md `ChatTraceStep`, research.md
 * Decision 3). `detail` stays loosely typed; per-stage interpretation lives in `TraceDialogComponent`.
 */
export interface ChatTraceStep {
  stage: string;
  durationMs: number;
  detail: Record<string, unknown>;
}

/** One entry in the conversation, in submission order (FR-018, data-model.md `ChatMessage`). */
export interface ChatMessage {
  id: string;
  role: ChatRole;
  text: string;
  citations: Citation[];
  status: ChatMessageStatus;
  errorMessage?: string;
  /** Present exactly when `ChatResponse.trace` was present on the response that settled this
   * message (010-chat-trace-dialog FR-002) — a direct pass-through, never re-derived (research.md
   * Decision 4). `undefined` for every `role: 'user'` message. */
  trace?: ChatTraceStep[];
}

/** `POST /chat`'s request body shape (007-chat-endpoint/contracts/chat-api-contract.md). */
export interface ChatRequestBody {
  question: string;
  documentIds: null;
  /** Always sent explicitly — `true` by default, `false` after the trace toggle is turned off
   * (FR-011/FR-012, data-model.md `ChatRequestBody`). */
  includeTrace: boolean;
}

/** One entry in `ChatResponse.sources` (007's data-model.md `SourceCitation`). */
export interface SourceCitation {
  documentId: string;
  filename: string;
  page: string;
  score: number;
}

/** `POST /chat`'s response body shape (007-chat-endpoint/contracts/chat-api-contract.md). */
export interface ChatResponse {
  answer: string;
  sources: SourceCitation[];
  /** Present exactly when the request had `includeTrace: true` (009's FR-010/FR-011) —
   * `undefined` when the JSON key is absent, never `null`. */
  trace?: ChatTraceStep[];
}

/**
 * Maps `ChatResponse.sources` to the client-side `Citation[]` shown under an assistant message.
 * Order, `documentId`, `filename`, and `pageLabel` are passed through verbatim; only `scorePercent`
 * is derived (a rounded percentage of `score`, research.md Decision 7). An empty list maps to an
 * empty list, matching the backend's own invariant that `sources` is empty exactly when `answer` is
 * the fixed "not covered" string (FR-003).
 */
export function mapSourcesToCitations(sources: SourceCitation[]): Citation[] {
  return sources.map((source) => ({
    documentId: source.documentId,
    filename: source.filename,
    pageLabel: source.page,
    scorePercent: Math.round(source.score * 100),
    available: true,
  }));
}
