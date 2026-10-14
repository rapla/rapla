import { afterEach, describe, expect, it, vi } from 'vitest';

import { storeActAs } from './act-as';
import { openDocument, withActAs } from './open-document';

/** PRD 131 D12 — a document opened from the SPA names this tab's act-as and cannot reach back (noopener). */
describe('openDocument (tier 5, no TestBed)', () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('appends actAs=self to a document URL without a stored target', () => {
    expect(withActAs('/api/documents/leihschein?eventId=e1')).toBe(
      '/api/documents/leihschein?eventId=e1&actAs=self',
    );
  });

  it('starts the query when there is none and carries the stored id', () => {
    storeActAs('u 1');
    expect(withActAs('/api/documents/leihschein')).toBe('/api/documents/leihschein?actAs=u%201');
  });

  it('opens a new window with noopener', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    openDocument('/api/documents/leihschein?eventId=e1');
    expect(open).toHaveBeenCalledWith(
      '/api/documents/leihschein?eventId=e1&actAs=self',
      '_blank',
      'noopener',
    );
  });
});
