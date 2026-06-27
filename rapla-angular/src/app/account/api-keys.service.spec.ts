import { beforeEach, afterEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ApiKeysService, ApiKeyCreated, ApiKeyMetadata } from './api-keys.service';

describe('ApiKeysService', () => {
  let service: ApiKeysService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiKeysService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiKeysService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs the key list', () => {
    const keys: ApiKeyMetadata[] = [
      { id: 'k1', label: 'CI', alg: 'RS256', thumbprint: 'k1', createdAt: 't', expiresAt: null, scopes: ['read'] },
    ];
    let received: ApiKeyMetadata[] | undefined;
    service.list().subscribe((r) => (received = r));
    const req = http.expectOne('/api/auth/api-keys');
    expect(req.request.method).toBe('GET');
    req.flush(keys);
    expect(received).toEqual(keys);
  });

  it('POSTs a create request and returns the one-time key', () => {
    const created: ApiKeyCreated = {
      id: 'k2', label: 'bot', alg: 'RS256', thumbprint: 'k2',
      createdAt: 't', expiresAt: null, scopes: ['read', 'write_events'], key: 'eyJ.JWT.sig',
    };
    let received: ApiKeyCreated | undefined;
    service.create({ label: 'bot', expiresInDays: null, scopes: ['read', 'write_events'] }).subscribe(
      (r) => (received = r),
    );
    const req = http.expectOne('/api/auth/api-keys');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ label: 'bot', expiresInDays: null, scopes: ['read', 'write_events'] });
    req.flush(created);
    expect(received?.key).toBe('eyJ.JWT.sig');
  });

  it('POSTs rotate with the graceMinutes param', () => {
    const rotated: ApiKeyCreated = {
      id: 'k9', label: 'CI', alg: 'RS256', thumbprint: 'k9', createdAt: 't', expiresAt: null,
      scopes: ['read'], key: 'eyJ.NEW',
    };
    let received: ApiKeyCreated | undefined;
    service.rotate('k1', 180).subscribe((r) => (received = r));
    const req = http.expectOne((r) => r.url === '/api/auth/api-keys/k1/rotate');
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('graceMinutes')).toBe('180');
    req.flush(rotated);
    expect(received?.key).toBe('eyJ.NEW');
  });

  it('DELETEs by id', () => {
    service.revoke('k1').subscribe();
    const req = http.expectOne('/api/auth/api-keys/k1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
