/**
 * The client-side Connection Status state machine (data-model.md). `checking` is the transient
 * initial value held only until the first poll resolves (research.md Decision 5); it is never
 * re-entered afterward.
 */
export type ConnectionStatus = 'checking' | 'healthy' | 'degraded' | 'unreachable';

/** data-model.md Entity: Connection Status. */
export interface ConnectionStatusInfo {
  state: ConnectionStatus;
  lastCheckedAt: Date;
}

/**
 * Classifies a health-endpoint response body into a {@link ConnectionStatus}, reading exactly one
 * field — top-level `status` (research.md Decision 4). Anything that isn't a plain object with a
 * string `status` field is treated as `unreachable` (FR-009), the same as a body this function
 * never sees at all because the request itself failed or didn't parse as JSON. `status: "UP"` is
 * `healthy` regardless of `components.azureOpenAi` (spec Story 3 Scenario 2) because this function
 * never reads `components` at all.
 */
export function classify(body: unknown): ConnectionStatus {
  if (typeof body === 'object' && body !== null && 'status' in body) {
    const status = (body as { status: unknown }).status;
    if (status === 'UP') {
      return 'healthy';
    }
    if (status === 'DOWN') {
      return 'degraded';
    }
  }
  return 'unreachable';
}
