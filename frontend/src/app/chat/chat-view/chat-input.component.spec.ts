import { TestBed } from '@angular/core/testing';
import { ChatInputComponent } from './chat-input.component';

describe('ChatInputComponent (presentational, FR-001/FR-004/FR-005/FR-006)', () => {
  function setup() {
    const fixture = TestBed.createComponent(ChatInputComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const input = () =>
      el.querySelector('input, textarea') as HTMLInputElement | HTMLTextAreaElement;
    const sendBtn = () => el.querySelector('.send-btn') as HTMLButtonElement;
    return { fixture, el, input, sendBtn };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ChatInputComponent] });
  });

  it('disables the send control when the trimmed input is empty (FR-004)', () => {
    const { fixture, input, sendBtn } = setup();
    input().value = '   ';
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(sendBtn().disabled).toBe(true);
  });

  it('enables the send control once there is real input', () => {
    const { fixture, input, sendBtn } = setup();
    input().value = 'a question';
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(sendBtn().disabled).toBe(false);
  });

  it('shows a live indicator and blocks submission once trimmed input reaches 1000 characters (FR-005)', () => {
    const { fixture, input, sendBtn, el } = setup();
    input().value = 'a'.repeat(1000);
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(sendBtn().disabled).toBe(true);
    expect(el.textContent).toContain('1000');
  });

  it('emits submit with the trimmed question and clears the input on send-button click (FR-001)', () => {
    const { fixture, input, sendBtn } = setup();
    const component = fixture.componentInstance;
    const emitted: string[] = [];
    component.submitQuestion.subscribe((q: string) => emitted.push(q));

    input().value = '  a real question  ';
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    sendBtn().click();
    fixture.detectChanges();

    expect(emitted).toEqual(['a real question']);
    expect(input().value).toBe('');
  });

  it('pressing Enter has the same effect as clicking send (FR-001)', () => {
    const { fixture, input } = setup();
    const component = fixture.componentInstance;
    const emitted: string[] = [];
    component.submitQuestion.subscribe((q: string) => emitted.push(q));

    input().value = 'enter question';
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    input().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    expect(emitted).toEqual(['enter question']);
  });

  it('disables both the input and the send control while pending is true (FR-006)', () => {
    const { fixture, input, sendBtn } = setup();
    fixture.componentRef.setInput('pending', true);
    fixture.detectChanges();

    expect(input().disabled).toBe(true);
    expect(sendBtn().disabled).toBe(true);
  });
});
