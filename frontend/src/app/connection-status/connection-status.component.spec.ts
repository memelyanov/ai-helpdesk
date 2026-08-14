import { TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';
import { ConnectionStatusComponent } from './connection-status.component';
import { HealthService } from './health.service';
import { ConnectionStatus } from './connection-status';

describe('ConnectionStatusComponent', () => {
  let status: WritableSignal<ConnectionStatus>;

  beforeEach(() => {
    status = signal<ConnectionStatus>('checking');
    TestBed.configureTestingModule({
      imports: [ConnectionStatusComponent],
      providers: [{ provide: HealthService, useValue: { status } }],
    });
  });

  /** Sets the stubbed status and reads back the rendered label's text and CSS class. */
  function renderFor(fixture: ReturnType<typeof TestBed.createComponent>, state: ConnectionStatus) {
    status.set(state);
    fixture.detectChanges();
    const label = (fixture.nativeElement as HTMLElement).querySelector('.connection-status__label');
    return { text: label?.textContent?.trim(), className: label?.className };
  }

  it('renders visibly different content for checking than for healthy (FR-003)', () => {
    const fixture = TestBed.createComponent(ConnectionStatusComponent);
    const checking = renderFor(fixture, 'checking');
    const healthy = renderFor(fixture, 'healthy');

    expect(checking.text).toBeTruthy();
    expect(healthy.text).toBeTruthy();
    expect(checking.text).not.toBe(healthy.text);
  });

  it('renders content for unreachable that is visually distinct from both healthy and checking (FR-003, spec Story 2)', () => {
    const fixture = TestBed.createComponent(ConnectionStatusComponent);
    const healthy = renderFor(fixture, 'healthy');
    const checking = renderFor(fixture, 'checking');
    const unreachable = renderFor(fixture, 'unreachable');

    expect(unreachable.text).not.toBe(healthy.text);
    expect(unreachable.className).not.toBe(healthy.className);
    expect(unreachable.text).not.toBe(checking.text);
    expect(unreachable.className).not.toBe(checking.className);
  });

  it('renders content for degraded that is visually distinct from both healthy and unreachable (FR-003, spec Story 3)', () => {
    const fixture = TestBed.createComponent(ConnectionStatusComponent);
    const healthy = renderFor(fixture, 'healthy');
    const unreachable = renderFor(fixture, 'unreachable');
    const degraded = renderFor(fixture, 'degraded');

    expect(degraded.text).not.toBe(healthy.text);
    expect(degraded.className).not.toBe(healthy.className);
    expect(degraded.text).not.toBe(unreachable.text);
    expect(degraded.className).not.toBe(unreachable.className);
  });
});
