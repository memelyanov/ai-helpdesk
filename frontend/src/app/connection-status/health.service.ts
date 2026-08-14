import { Injectable, Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, map, of, switchMap, timeout, timer } from 'rxjs';
import { ConnectionStatus, classify } from './connection-status';

/** Fixed local address (plan.md Technical Context / spec Assumptions — no deployment target yet). */
export const HEALTH_ENDPOINT = 'http://localhost:8080/actuator/health';

/** research.md Decision 2: poll every 10s starting at t=0; bound each request to 3s. */
const POLL_INTERVAL_MS = 10000;
const REQUEST_TIMEOUT_MS = 3000;

/**
 * Polls the backend's health endpoint on a fixed interval and exposes the classified result as a
 * signal (research.md Decision 1). `catchError` sits inside the per-tick `switchMap` so that one
 * failed or timed-out check does not end the polling stream (FR-006, FR-007) — the next scheduled
 * tick always fires regardless of the previous one's outcome.
 *
 * `HttpClient` treats any non-2xx response (e.g. the degraded case's `503`) as an error, not a
 * `next` emission — so `catchError` must itself distinguish "the server responded, just not with
 * 2xx" (`HttpErrorResponse` with a non-zero `status`, i.e. reachable per spec Definitions) from
 * "no response was received at all" (`status === 0` for a network/CORS failure, or any other
 * thrown error such as `timeout()`'s `TimeoutError`). Collapsing both into `unreachable`
 * unconditionally would misclassify a degraded backend as unreachable, violating FR-003.
 */
@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);

  readonly status: Signal<ConnectionStatus> = toSignal(
    timer(0, POLL_INTERVAL_MS).pipe(
      switchMap(() =>
        this.http.get(HEALTH_ENDPOINT).pipe(
          timeout(REQUEST_TIMEOUT_MS),
          map(classify),
          catchError((error: unknown) => {
            if (error instanceof HttpErrorResponse && error.status !== 0) {
              // The server responded (e.g. 503) — classify whatever body it sent.
              return of(classify(error.error));
            }
            return of<ConnectionStatus>('unreachable');
          }),
        ),
      ),
    ),
    { initialValue: 'checking' },
  );
}
