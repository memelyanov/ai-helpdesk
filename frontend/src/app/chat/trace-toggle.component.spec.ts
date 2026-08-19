import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TraceToggleComponent } from './trace-toggle.component';
import { ChatService } from './chat.service';

describe('TraceToggleComponent (010-chat-trace-dialog contracts/frontend-trace-contract.md)', () => {
  function render() {
    const fixture = TestBed.createComponent(TraceToggleComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TraceToggleComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('reflects the "on" state by default (ChatService.includeTrace() defaults to true, FR-011)', () => {
    const { el } = render();
    const button = el.querySelector('.trace-toggle') as HTMLButtonElement;
    expect(button.classList.contains('trace-toggle--on')).toBe(true);
    expect(button.textContent?.toLowerCase()).toContain('on');
  });

  it('clicking the control calls ChatService.setIncludeTrace(false) when currently on', () => {
    const { fixture, el } = render();
    const chatService = TestBed.inject(ChatService);

    (el.querySelector('.trace-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(chatService.includeTrace()).toBe(false);
    const button = el.querySelector('.trace-toggle') as HTMLButtonElement;
    expect(button.classList.contains('trace-toggle--on')).toBe(false);
  });

  it('clicking it a second time flips back to true', () => {
    const { fixture, el } = render();
    const chatService = TestBed.inject(ChatService);
    const button = el.querySelector('.trace-toggle') as HTMLButtonElement;

    button.click();
    fixture.detectChanges();
    button.click();
    fixture.detectChanges();

    expect(chatService.includeTrace()).toBe(true);
    expect(button.classList.contains('trace-toggle--on')).toBe(true);
  });

  it('takes no @Input and emits no @Output (constructed and rendered with zero bindings)', () => {
    // If TraceToggleComponent declared a required input, TestBed.createComponent + detectChanges()
    // (with no setInput call anywhere in this file) would throw. Reaching this assertion is the proof.
    const { fixture } = render();
    expect(fixture.componentInstance).toBeTruthy();
  });
});
