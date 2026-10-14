import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpEvent, HttpRequest, HttpResponse } from '@angular/common/http';
import { type Observable, of } from 'rxjs';

import { actAsId, actAsInterceptor, storeActAs } from './act-as';

/** PRD 131 D7/D12 — the tab's act-as id and the interceptor that names it on every /api request. */
describe('actAsInterceptor (tier 5, no TestBed)', () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  const sent = (url: string): HttpRequest<unknown> => {
    let seen!: HttpRequest<unknown>;
    const next = (req: HttpRequest<unknown>): Observable<HttpEvent<unknown>> => {
      seen = req;
      return of(new HttpResponse());
    };
    actAsInterceptor(new HttpRequest('GET', url), next).subscribe();
    return seen;
  };

  it('sends self when the tab acts as nobody', () => {
    expect(sent('/api/auth/me').headers.get('X-Rapla-Act-As')).toBe('self');
  });

  it('sends the stored user id once the tab impersonates', () => {
    storeActAs('u-export');
    expect(sent('/api/graphql').headers.get('X-Rapla-Act-As')).toBe('u-export');
  });

  it('forgets the id again on storeActAs(null)', () => {
    storeActAs('u-export');
    storeActAs(null);
    expect(actAsId()).toBe('self');
    expect(sessionStorage.length).toBe(0);
  });

  it('leaves everything but relative /api requests alone', () => {
    storeActAs('u-export');
    expect(sent('/assets/i18n/de.json').headers.has('X-Rapla-Act-As')).toBe(false);
    expect(sent('https://idp.example.org/api/token').headers.has('X-Rapla-Act-As')).toBe(false);
  });

  it('acts as self when sessionStorage is blocked', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(sent('/api/auth/me').headers.get('X-Rapla-Act-As')).toBe('self');
  });
});
