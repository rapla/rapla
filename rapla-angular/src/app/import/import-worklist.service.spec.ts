import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ImportWorklistService } from './import-worklist.service';
import { FilterStore } from '../state/filter-store';
import { AuthService } from '../auth/auth.service';

/** PRD 123 D6 — a 404 on the import metadata endpoint means "no import plugin"; the answer is
 *  final for the session, not retried on every selection change. */
describe('ImportWorklistService metadata', () => {
  let svc: ImportWorklistService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        ImportWorklistService,
        FilterStore,
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    svc = TestBed.inject(ImportWorklistService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the metadata once after a 404', () => {
    svc.ensureLoaded().subscribe();
    http
      .expectOne('/api/externaleventimport/metadata')
      .flush('', { status: 404, statusText: 'Not Found' });
    svc.ensureLoaded().subscribe();
    http.expectNone('/api/externaleventimport/metadata');
    expect(svc.sourceName()).toBe('');
  });

  it('retries the metadata after a network error', () => {
    svc.ensureLoaded().subscribe();
    http.expectOne('/api/externaleventimport/metadata').error(new ProgressEvent('error'));
    svc.ensureLoaded().subscribe();
    http.expectOne('/api/externaleventimport/metadata').flush({ sourceName: 'Campus' });
    expect(svc.sourceName()).toBe('Campus');
  });
});
