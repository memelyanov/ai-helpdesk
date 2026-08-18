import { Component, inject } from '@angular/core';
import { ChatService } from './chat.service';

/**
 * Self-contained control for turning diagnostic trace collection off (or back on), the same
 * self-injecting shape `ConnectionStatusComponent` already uses for `HealthService`
 * (research.md Decision 2). No `@Input`/`@Output` — reads and writes `ChatService.includeTrace`
 * directly, so a consumer places `<app-trace-toggle />` anywhere with no wiring required.
 */
@Component({
  selector: 'app-trace-toggle',
  imports: [],
  templateUrl: './trace-toggle.component.html',
  styleUrl: './trace-toggle.component.css',
})
export class TraceToggleComponent {
  private readonly chatService = inject(ChatService);

  readonly includeTrace = this.chatService.includeTrace;

  toggle(): void {
    this.chatService.setIncludeTrace(!this.chatService.includeTrace());
  }
}
