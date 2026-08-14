import { classify } from './connection-status';

/**
 * The real response captured from a running backend (contracts/frontend-health-consumption.md
 * "Reference fixture"), used in preference to a hand-trimmed `{ status: 'UP' }` stub so this test
 * proves indifference to `diskSpace`/`ssl`/extra components instead of merely assuming it
 * (research.md Decision 6).
 */
const healthyFixture = {
  status: 'UP',
  components: {
    azureOpenAi: {
      status: 'UP',
      details: { configured: true, endpointConfigured: true, chatDeploymentConfigured: true },
    },
    db: {
      status: 'UP',
      details: { database: 'PostgreSQL', validationQuery: 'isValid()' },
    },
    diskSpace: {
      status: 'UP',
      details: {
        total: 509218910208,
        free: 163631063040,
        threshold: 10485760,
        path: 'C:\\Epam\\ai-helpdesk\\backend\\.',
        exists: true,
      },
    },
    ping: { status: 'UP' },
    ssl: {
      status: 'UP',
      details: { validChains: [], invalidChains: [] },
    },
  },
};

describe('classify', () => {
  it('maps a response body with status "UP" to healthy (FR-004)', () => {
    expect(classify(healthyFixture)).toBe('healthy');
  });

  it('maps a response body with status "DOWN" to degraded (FR-005)', () => {
    expect(classify({ status: 'DOWN', components: { db: { status: 'DOWN' } } })).toBe('degraded');
  });

  it('maps status "UP" with an UNKNOWN azureOpenAi component to healthy, not a separate state (spec Story 3 Scenario 2)', () => {
    expect(
      classify({
        ...healthyFixture,
        status: 'UP',
        components: {
          ...healthyFixture.components,
          azureOpenAi: { status: 'UNKNOWN', details: { configured: false, missing: ['api-key'] } },
        },
      }),
    ).toBe('healthy');
  });

  // FR-009 / spec Edge Cases: a reachable-but-malformed body must not crash the page or be
  // surfaced as anything other than unreachable. True JSON-parse failure (bytes that never become
  // a JS value at all) is exercised end-to-end through HttpTestingController in
  // health.service.spec.ts, since that failure surfaces as an Observable error before classify()
  // is ever called; the cases below are what classify() itself can receive: a parsed value whose
  // shape doesn't match what's expected.
  describe('malformed body (FR-009)', () => {
    it('maps a body missing the status field to unreachable', () => {
      expect(classify({ components: {} })).toBe('unreachable');
    });

    it('maps a body whose status field is not a string to unreachable', () => {
      expect(classify({ status: 200 })).toBe('unreachable');
    });

    it('maps a non-object body to unreachable', () => {
      expect(classify('not an object')).toBe('unreachable');
      expect(classify(null)).toBe('unreachable');
      expect(classify(undefined)).toBe('unreachable');
    });
  });
});
