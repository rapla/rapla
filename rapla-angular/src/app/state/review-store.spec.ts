import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ReviewStore } from './review-store';
import { FilterStore, type FilterEntry } from './filter-store';
import type { ConflictWire, RequestWire } from './review-tree';

const conflict = (id: string, resourceId: string, disabled = false): ConflictWire => ({
  id,
  startDate: '2026-10-05T10:00:00',
  disabled,
  description: 'belegt',
  resource: { id: resourceId, name: `Raum ${resourceId}` },
  reservation1: { name: 'A' },
  reservation2: { name: 'B' },
});
const request = (resourceId: string, reservationId: string): RequestWire => ({
  resource: { id: resourceId, name: 'Beamer' },
  reservationId,
  reservation: { name: 'Tagung' },
  appointments: [{ start: '2026-10-06T09:00:00' }],
});
const bucket = (resourceId: string, disabled: boolean, count: number) => ({
  keys: [
    { key: 'RESOURCE', value: resourceId },
    { key: 'DISABLED', value: String(disabled) },
  ],
  count,
});

describe('ReviewStore — PRD 128 Prüfen data', () => {
  let store: ReviewStore;
  let filter: FilterStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(ReviewStore);
    filter = TestBed.inject(FilterStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const flushHeads = (buckets: unknown[], requests: RequestWire[]) => {
    const req = http.expectOne((r) => String(r.body?.query).includes('conflictStats'));
    expect(req.request.body.query).toContain('groupBy: [RESOURCE, DISABLED]');
    req.flush({ data: { conflictStats: buckets, resourceRequests: requests } });
  };
  const flushConflicts = (resourceIds: string[], conflicts: ConflictWire[]) => {
    const req = http.expectOne((r) => String(r.body?.query).includes('conflicts(filter'));
    expect(req.request.body.variables).toEqual({ f: { resourceIdsIn: resourceIds } });
    req.flush({ data: { conflicts } });
  };

  it('loads the section heads once: conflict counts per resource and state, all open requests (OQ7)', () => {
    store.ensureLoaded();
    store.ensureLoaded();
    TestBed.tick();
    flushHeads([bucket('r1', false, 3), bucket('r1', true, 1)], [request('b1', 'v1')]);
    expect(store.counts()).toEqual([
      { resourceId: 'r1', disabled: false, count: 3 },
      { resourceId: 'r1', disabled: true, count: 1 },
    ]);
    expect(store.requests()).toEqual([request('b1', 'v1')]);
  });

  it('loads the conflicts of one resource only when asked, once (D5)', () => {
    store.loadConflicts('r1');
    flushConflicts(['r1'], [conflict('k1', 'r1')]);
    store.loadConflicts('r1');
    expect(store.loaded().get('r1')).toEqual([conflict('k1', 'r1')]);
  });

  it('switching to Konflikte reloads the heads and loaded resources and drops stale stored conflicts (OQ8/OQ19)', () => {
    store.loadConflicts('r1');
    flushConflicts(['r1'], [conflict('k1', 'r1')]);
    const stored = (id: string): FilterEntry => ({ id, kind: 'conflict', label: 'old' });
    filter.setContext('conflicts');
    filter.setAll([stored('CONFLICT;r2;a;b'), stored('CONFLICT;r2;gone;x')]);
    store.refresh('conflicts');
    flushHeads([bucket('r2', false, 1)], []);
    flushConflicts(['r1', 'r2'], [conflict('CONFLICT;r2;a;b', 'r2')]);
    expect(filter.entries()).toEqual([
      {
        id: 'CONFLICT;r2;a;b',
        kind: 'conflict',
        label: '⚠ Raum r2 · 05.10.',
        resourceName: 'Raum r2',
      },
    ]);
    expect(store.loaded().get('r1')).toEqual([]);
  });

  it('switching to Ressourcenanfragen drops requests that are no longer open (OQ19)', () => {
    filter.setContext('requests');
    filter.setAll([
      { id: 'REQUEST;b1;v1', kind: 'request', label: 'old' },
      { id: 'REQUEST;b1;v2', kind: 'request', label: 'approved' },
    ]);
    store.refresh('requests');
    flushHeads([], [request('b1', 'v1')]);
    expect(filter.entries().map((e) => [e.id, e.label])).toEqual([
      ['REQUEST;b1;v1', '? Beamer · Tagung'],
    ]);
  });
});
