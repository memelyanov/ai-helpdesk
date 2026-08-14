import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HealthService, HEALTH_ENDPOINT } from './health.service';

/** Same fixture as connection-status.spec.ts — see that file for why it's the full real shape. */
const healthyFixture = {
  status: 'UP',
  components: {
    azureOpenAi: { status: 'UP', details: { configured: true } },
    db: { status: 'UP', details: { database: 'PostgreSQL' } },
    diskSpace: { status: 'UP', details: { total: 1, free: 1, threshold: 1, exists: true } },
    ping: { status: 'UP' },
    ssl: { status: 'UP', details: { validChains: [], invalidChains: [] } },
  },
};

describe('HealthService', () => {
  let httpMock: HttpTestingController;
  let service: HealthService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HealthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('starts at checking, then becomes healthy once the first request resolves (FR-002, research.md Decision 5)', () => {
    expect(service.status()).toBe('checking');

    vi.advanceTimersByTime(0);
    const req = httpMock.expectOne(HEALTH_ENDPOINT);
    req.flush(healthyFixture);

    expect(service.status()).toBe('healthy');
  });

  it('treats a 200 response with a malformed/missing-status body as unreachable (FR-009)', () => {
    vi.advanceTimersByTime(0);
    const req = httpMock.expectOne(HEALTH_ENDPOINT);
    req.flush({ components: {} });

    expect(service.status()).toBe('unreachable');
  });

  it('treats a network error as unreachable (spec Definitions — Unreachable)', () => {
    vi.advanceTimersByTime(0);
    const req = httpMock.expectOne(HEALTH_ENDPOINT);
    req.error(new ProgressEvent('error'));

    expect(service.status()).toBe('unreachable');
  });

  it('treats a request left hanging past the 3s per-request timeout as unreachable rather than waiting indefinitely (FR-007)', () => {
    vi.advanceTimersByTime(0);
    httpMock.expectOne(HEALTH_ENDPOINT); // never flushed — simulates a hung backend

    vi.advanceTimersByTime(3000);

    expect(service.status()).toBe('unreachable');
  });

  it('returns to healthy on the next scheduled poll after an unreachable result, without recreating the service (FR-006, spec Story 2 Scenario 3)', () => {
    vi.advanceTimersByTime(0);
    const firstReq = httpMock.expectOne(HEALTH_ENDPOINT);
    firstReq.error(new ProgressEvent('error'));
    expect(service.status()).toBe('unreachable');

    vi.advanceTimersByTime(10000);
    const secondReq = httpMock.expectOne(HEALTH_ENDPOINT);
    secondReq.flush(healthyFixture);

    expect(service.status()).toBe('healthy');
  });

  it('treats a 503 response with status "DOWN" as degraded (FR-005)', () => {
    vi.advanceTimersByTime(0);
    const req = httpMock.expectOne(HEALTH_ENDPOINT);
    req.flush(
      { status: 'DOWN', components: { db: { status: 'DOWN' } } },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(service.status()).toBe('degraded');
  });
});
