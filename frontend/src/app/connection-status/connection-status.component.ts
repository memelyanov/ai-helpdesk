import { Component, inject } from '@angular/core';
import { HealthService } from './health.service';

/**
 * Renders the current {@link ConnectionStatus} as a small always-visible indicator (FR-001).
 * `healthy` and `checking` each get distinct markup (User Story 1); `degraded` and `unreachable`
 * share one fallback branch here, differentiated from each other in later user stories
 * (User Story 2 / User Story 3) once their own failing tests exist — see connection-status.ts.
 */
@Component({
  selector: 'app-connection-status',
  imports: [],
  templateUrl: './connection-status.component.html',
  styleUrl: './connection-status.component.css',
})
export class ConnectionStatusComponent {
  private readonly healthService = inject(HealthService);
  readonly status = this.healthService.status;
}
