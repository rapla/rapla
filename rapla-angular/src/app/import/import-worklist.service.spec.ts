import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { signal } from '@angular/core';
import { firstValueFrom, of, throwError, type Observable } from 'rxjs';
import { GraphqlService } from '../graphql/graphql.service';
import { ResourceSelectionStore } from '../state/resource-selection-store';
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

/** WP23x C2/C3 + review round 18 M3: Übernehmen / Verwerfen / suggestions keep refusal and
 *  technical error apart and never throw into the dialog. */
describe('ImportWorklistService staging mutations', () => {
  let svc: ImportWorklistService;
  const gql = { mutate: vi.fn(), query: vi.fn() };

  beforeEach(() => {
    gql.mutate.mockReset();
    gql.query.mockReset();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ImportWorklistService,
        FilterStore,
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: GraphqlService, useValue: gql },
        { provide: ResourceSelectionStore, useValue: { resources: signal([]) } },
      ],
    });
    svc = TestBed.inject(ImportWorklistService);
  });

  async function outcome<T>(o: Observable<T>): Promise<T> {
    return firstValueFrom(o);
  }

  it('applyStagedChanges: applied refreshes, refused and error do not', async () => {
    const refresh = vi.spyOn(svc, 'refresh').mockImplementation(() => undefined);
    gql.mutate.mockReturnValueOnce(of({ kind: 'ok', data: { applyStagedChanges: true } }));
    expect(await outcome(svc.applyStagedChanges('v:1'))).toBe('applied');
    gql.mutate.mockReturnValueOnce(of({ kind: 'ok', data: { applyStagedChanges: false } }));
    expect(await outcome(svc.applyStagedChanges('v:1'))).toBe('refused');
    gql.mutate.mockReturnValueOnce(of({ kind: 'transport', message: 'down' }));
    expect(await outcome(svc.applyStagedChanges('v:1'))).toBe('error');
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('dismissStagedChanges: count on success, null on error', async () => {
    vi.spyOn(svc, 'refresh').mockImplementation(() => undefined);
    gql.mutate.mockReturnValueOnce(of({ kind: 'ok', data: { dismissStagedChanges: 1 } }));
    expect(await outcome(svc.dismissStagedChanges(['v:1']))).toBe(1);
    gql.mutate.mockReturnValueOnce(throwError(() => new Error('boom')));
    expect(await outcome(svc.dismissStagedChanges(['v:1']))).toBeNull();
  });

  it('templateSuggestions and bindCandidates answer [] on error (M2)', async () => {
    gql.query.mockReturnValue(throwError(() => new Error('boom')));
    expect(await outcome(svc.templateSuggestions(['k1'], 'v'))).toEqual([]);
    expect(await outcome(svc.bindCandidates('v:1', 'a', 'b'))).toEqual([]);
  });

  it('stagedEventDetails: the record, or null on error / not visible (WP23x C4)', async () => {
    const record = {
      sourceItemId: 'v:1',
      changedSince: null,
      fields: [{ key: 'NAME', value: 'Mathe' }],
    };
    gql.query.mockReturnValueOnce(of({ data: { stagedEventDetails: record } }));
    expect(await outcome(svc.stagedEventDetails('v:1'))).toEqual(record);
    gql.query.mockReturnValueOnce(of({ data: { stagedEventDetails: null } }));
    expect(await outcome(svc.stagedEventDetails('v:2'))).toBeNull();
    gql.query.mockReturnValueOnce(throwError(() => new Error('boom')));
    expect(await outcome(svc.stagedEventDetails('v:3'))).toBeNull();
  });
});
