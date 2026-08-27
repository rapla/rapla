import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { GraphqlService } from '../graphql/graphql.service';
import { EventDataService } from './event-data.service';
import { newDraft } from './event-draft';
import { TemplateInstantiationService } from './template-instantiation.service';

/**
 * PRD 104 D9 — v1 instantiates only the FIRST reservation of a template. That is a decided
 * deferral; instantiating a "Semestervorlage" and silently dropping n-1 events is not. Swing
 * (`EditTaskPresenter` → `copyReservations`) creates them all, so the SPA must at least say
 * what it left behind.
 */
describe('TemplateInstantiationService', () => {
  function setup(ids: { id: string }[]) {
    const query = vi.fn(() => of({ data: { reservationsFromTemplate: ids } }));
    const load = vi.fn(() => of({ draft: newDraft('event', new Date('2026-07-07T12:00:00')) }));
    const open = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        { provide: GraphqlService, useValue: { query } },
        { provide: EventDataService, useValue: { load } },
        { provide: MatSnackBar, useValue: { open } },
      ],
    });
    return { service: TestBed.inject(TemplateInstantiationService), open };
  }

  it('a single-reservation template instantiates silently', () => {
    const { service, open } = setup([{ id: 'r1' }]);
    let draft: unknown;
    service.instantiate('t1', null).subscribe((d) => (draft = d));
    expect(draft).toBeTruthy();
    expect(open).not.toHaveBeenCalled();
  });

  it('a multi-reservation template names what it dropped', () => {
    const { service, open } = setup([{ id: 'r1' }, { id: 'r2' }, { id: 'r3' }]);
    let draft: unknown;
    service.instantiate('t1', null).subscribe((d) => (draft = d));
    expect(draft).toBeTruthy();
    expect(open).toHaveBeenCalledTimes(1);
    const message = open.mock.calls[0][0] as string;
    expect(message).toContain('3');
    expect(message).toContain('erste');
  });

  it('an empty template yields null and no truncation notice', () => {
    const { service, open } = setup([]);
    let draft: unknown = 'unset';
    service.instantiate('t1', null).subscribe((d) => (draft = d));
    expect(draft).toBeNull();
    expect(open).not.toHaveBeenCalled();
  });
});
