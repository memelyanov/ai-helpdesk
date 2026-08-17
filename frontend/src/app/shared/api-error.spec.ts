import {
  mapChatError,
  mapUploadError,
  mapDownloadError,
  mapDeleteError,
  DOCUMENT_NOT_FOUND_MESSAGE,
  DOWNLOAD_FAILED_MESSAGE,
} from './api-error';

describe('mapChatError (FR-007, chat-api-contract.md)', () => {
  it.each([
    'blank_question',
    'question_too_long',
    'malformed_request',
    'provider_unconfigured',
    'processing_failed',
  ])('returns a non-empty, human-readable message for %s', (code) => {
    const message = mapChatError(code);
    expect(message).toBeTruthy();
    expect(message).not.toBe(code);
  });

  it('gives every documented code a distinct message from every other documented code', () => {
    const codes = [
      'blank_question',
      'question_too_long',
      'malformed_request',
      'provider_unconfigured',
      'processing_failed',
    ];
    const messages = codes.map(mapChatError);
    expect(new Set(messages).size).toBe(codes.length);
  });

  it('falls back to a generic message for an unrecognized code (research.md Decision 5)', () => {
    expect(mapChatError('some_future_code')).toBeTruthy();
  });

  it('falls back to a generic message for a network-level failure with no response (code null)', () => {
    expect(mapChatError(null)).toBeTruthy();
    expect(mapChatError(undefined)).toBeTruthy();
  });
});

describe('mapUploadError (FR-011, ingestion-api-contract.md)', () => {
  it.each(['unsupported_type', 'invalid_file', 'unparseable'])(
    'gives %s its own distinct message',
    (code) => {
      expect(mapUploadError(code)).toBeTruthy();
    },
  );

  it('gives unsupported_type, invalid_file, and unparseable three different messages', () => {
    const messages = ['unsupported_type', 'invalid_file', 'unparseable'].map(mapUploadError);
    expect(new Set(messages).size).toBe(3);
  });

  it('maps provider_unconfigured and processing_failed to the SAME "service unavailable" message (research.md Decision 5 clarification)', () => {
    expect(mapUploadError('provider_unconfigured')).toBe(mapUploadError('processing_failed'));
  });

  it('treats a network-level failure (code null) identically to provider_unconfigured/processing_failed (FR-011)', () => {
    expect(mapUploadError(null)).toBe(mapUploadError('provider_unconfigured'));
    expect(mapUploadError(undefined)).toBe(mapUploadError('processing_failed'));
  });

  it('falls back to a message for an unrecognized code, distinct from the network-failure case being possible too', () => {
    expect(mapUploadError('some_future_code')).toBeTruthy();
  });
});

describe('mapDownloadError (FR-014, document-query-api-contract.md)', () => {
  it('maps document_not_found to the fixed "no longer available" message', () => {
    expect(mapDownloadError('document_not_found')).toBe(DOCUMENT_NOT_FOUND_MESSAGE);
  });

  it('maps any other cause (including a network failure) to the generic retryable download-failed message', () => {
    expect(mapDownloadError(null)).toBe(DOWNLOAD_FAILED_MESSAGE);
    expect(mapDownloadError('some_other_code')).toBe(DOWNLOAD_FAILED_MESSAGE);
  });

  it('gives the not-found and generic-failure messages different text', () => {
    expect(DOCUMENT_NOT_FOUND_MESSAGE).not.toBe(DOWNLOAD_FAILED_MESSAGE);
  });
});

describe('mapDeleteError (FR-017, document-delete-api-contract.md)', () => {
  it('gives deletion_failed a specific message', () => {
    expect(mapDeleteError('deletion_failed')).toBeTruthy();
  });

  it('gives document_not_found a different, specific message', () => {
    const deletionFailed = mapDeleteError('deletion_failed');
    const notFound = mapDeleteError('document_not_found');
    expect(notFound).toBeTruthy();
    expect(notFound).not.toBe(deletionFailed);
  });

  it('falls back to a generic message for an unrecognized code or a network-level failure', () => {
    expect(mapDeleteError('some_future_code')).toBeTruthy();
    expect(mapDeleteError(null)).toBeTruthy();
  });
});
