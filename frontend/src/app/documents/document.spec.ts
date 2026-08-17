import { iconKindForContentType } from './document';

describe('iconKindForContentType (data-model.md DocumentSummary, mockup file-type icon)', () => {
  it('maps application/pdf to "pdf"', () => {
    expect(iconKindForContentType('application/pdf')).toBe('pdf');
  });

  it('maps text/plain to "text"', () => {
    expect(iconKindForContentType('text/plain')).toBe('text');
  });
});
