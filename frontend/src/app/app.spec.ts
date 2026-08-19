import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('renders a sidebar region and a main chat region (plan.md two-pane shell)', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.sidebar')).toBeTruthy();
    expect(compiled.querySelector('.main')).toBeTruthy();
  });

  it('still hosts <app-connection-status> inside the sidebar header', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const sidebar = compiled.querySelector('.sidebar');
    expect(sidebar?.querySelector('app-connection-status')).toBeTruthy();
  });

  it('no longer shows the "not yet implemented" placeholder text', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent?.toLowerCase()).not.toContain('not yet implemented');
  });

  it('hosts <app-trace-toggle> inside the chat header (010-chat-trace-dialog FR-012)', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const chatHeader = compiled.querySelector('.chat-header');
    expect(chatHeader?.querySelector('app-trace-toggle')).toBeTruthy();
  });
});
