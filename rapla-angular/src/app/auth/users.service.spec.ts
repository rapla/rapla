import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { UsersService } from './users.service';

/**
 * PRD 072 Phase 4 — cookie-credential UsersService. GET /api/users goes through
 * HttpClient (relative URL → browser auto-attaches the access_token cookie);
 * the SPA holds no token. A non-2xx collapses to [] so the toolbar chip stays
 * non-clickable.
 */
describe('UsersService (cookie model)', () => {
  let users: UsersService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UsersService, provideHttpClient(), provideHttpClientTesting()],
    });
    users = TestBed.inject(UsersService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('GETs /api/users (relative URL, no Authorization header — cookie carries auth)', async () => {
    const p = firstValueFrom(users.list());
    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([{ username: 'alice', displayName: 'Alice' }]);
    expect(await p).toEqual([{ username: 'alice', displayName: 'Alice' }]);
  });

  it('returns [] on a non-2xx response (chip stays non-clickable)', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const p = firstValueFrom(users.list());
    httpMock.expectOne('/api/users').flush(null, { status: 403, statusText: 'Forbidden' });
    expect(await p).toEqual([]);
  });
});
