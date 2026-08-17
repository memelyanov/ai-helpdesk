import { mapSourcesToCitations } from './chat-message';
import type { ChatResponse } from './chat-message';

describe('mapSourcesToCitations (data-model.md Citation, FR-002)', () => {
  it('rounds score to a whole-number percent (research.md Decision 7)', () => {
    const response: ChatResponse = {
      answer: 'Some grounded answer.',
      sources: [{ documentId: 'doc-1', filename: 'a.pdf', page: '3', score: 0.814 }],
    };

    const citations = mapSourcesToCitations(response.sources);

    expect(citations).toEqual([
      { documentId: 'doc-1', filename: 'a.pdf', pageLabel: '3', scorePercent: 81, available: true },
    ]);
  });

  it('passes the page label through verbatim, including the fixed "no page structure" marker', () => {
    const citations = mapSourcesToCitations([
      { documentId: 'doc-2', filename: 'b.txt', page: 'no page structure', score: 0.5 },
    ]);

    expect(citations[0].pageLabel).toBe('no page structure');
  });

  it('produces zero citations for an empty sources list (the fixed "not covered" answer, FR-003)', () => {
    const citations = mapSourcesToCitations([]);
    expect(citations).toEqual([]);
  });

  it('preserves the given order (most relevant first, per the backend response)', () => {
    const citations = mapSourcesToCitations([
      { documentId: 'doc-1', filename: 'a.pdf', page: '1', score: 0.9 },
      { documentId: 'doc-2', filename: 'b.pdf', page: '2', score: 0.6 },
    ]);
    expect(citations.map((c) => c.documentId)).toEqual(['doc-1', 'doc-2']);
  });

  it('every mapped citation starts available (FR-014, flips to false only after a 404 download)', () => {
    const citations = mapSourcesToCitations([
      { documentId: 'doc-1', filename: 'a.pdf', page: '1', score: 0.9 },
    ]);
    expect(citations[0].available).toBe(true);
  });
});
