import { describe, expect, it, vi } from 'vitest';
import { of, type Observable } from 'rxjs';

import { buildBulkDeleteCommand, buildMoveCommand } from './event-commands';
import type { MutationResult } from '../graphql/mutation-result';
import type { GraphqlService } from '../graphql/graphql.service';
import type { EventDataService } from '../event/event-data.service';
import type { EventDraft } from '../event/event-draft';

const ok: MutationResult<unknown> = { kind: 'ok', data: {} };
const concurrent: MutationResult<unknown> = { kind: 'concurrent', issues: [] };

function draft(id: string, name: string): EventDraft {
  return {
    id,
    persisted: true,
    typeKey: 'event',
    values: { name },
    appointments: [],
    allocations: [],
    lastChanged: '2026-06-01T10:00:00',
  };
}

describe('buildBulkDeleteCommand (PRD 099 Phase 3)', () => {
  it('executes ONE deleteReservations with all ids; plural label', () => {
    const mutate = vi.fn<(query: string, vars: unknown) => Observable<MutationResult<unknown>>>();
    mutate.mockReturnValue(of(ok));
    const gql = { mutate } as unknown as GraphqlService;
    const data = { save: vi.fn() } as unknown as EventDataService;
    const command = buildBulkDeleteCommand(gql, data, [draft('e-1', 'A'), draft('e-2', 'B')]);
    expect(command.label).toBe('2 Veranstaltungen gelöscht');
    command.execute().subscribe();
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate.mock.calls[0][1]).toEqual({ ids: ['e-1', 'e-2'] });
  });

  it('singular label at one event', () => {
    const gql = { mutate: vi.fn(() => of(ok)) } as unknown as GraphqlService;
    const data = {} as EventDataService;
    expect(buildBulkDeleteCommand(gql, data, [draft('e-1', 'A')]).label).toBe(
      '1 Veranstaltung gelöscht',
    );
  });

  it('undo re-creates every captured state with the SAME ids', () => {
    const save = vi.fn<(draft: EventDraft) => Observable<MutationResult<unknown>>>();
    save.mockReturnValue(of(ok));
    const gql = { mutate: vi.fn(() => of(ok)) } as unknown as GraphqlService;
    const data = { save } as unknown as EventDataService;
    const command = buildBulkDeleteCommand(gql, data, [draft('e-1', 'A'), draft('e-2', 'B')]);
    let result: MutationResult<unknown> | undefined;
    command.undo!().subscribe((r) => (result = r));
    expect(result).toEqual({ kind: 'ok', data: null });
    expect(save).toHaveBeenCalledTimes(2);
    const restored = save.mock.calls.map((c) => c[0]);
    expect(restored.map((d) => d.id)).toEqual(['e-1', 'e-2']);
    // save() routes to createReservation: not persisted, no concurrency token
    expect(restored.every((d) => !d.persisted && d.lastChanged === null)).toBe(true);
  });

  it('OQ2 best-effort: a mid-list failure does not stop the rest; the aggregate reports loudly', () => {
    const save = vi
      .fn()
      .mockReturnValueOnce(of(ok))
      .mockReturnValueOnce(of(concurrent))
      .mockReturnValueOnce(of(ok));
    const gql = { mutate: vi.fn(() => of(ok)) } as unknown as GraphqlService;
    const data = { save } as unknown as EventDataService;
    const command = buildBulkDeleteCommand(gql, data, [
      draft('e-1', 'A'),
      draft('e-2', 'Physik'),
      draft('e-3', 'C'),
    ]);
    let result: MutationResult<unknown> | undefined;
    command.undo!().subscribe((r) => (result = r));
    expect(save).toHaveBeenCalledTimes(3); // e-3 restored despite e-2 failing
    expect(result?.kind).toBe('invalid');
    const messages = (result as { issues: { message: string }[] }).issues.map((i) => i.message);
    expect(messages[0]).toBe('2 von 3 wiederhergestellt');
    expect(messages[1]).toContain('Physik');
    expect(messages[1]).toContain('inzwischen geändert');
  });
});

describe('buildMoveCommand (PRD 095 Phase 3b / week grid)', () => {
  it('executes moveReservations with an ISO minute shift; undo negates it', () => {
    const mutate = vi.fn<(query: string, vars: unknown) => Observable<MutationResult<unknown>>>();
    mutate.mockReturnValue(of(ok));
    const gql = { mutate } as unknown as GraphqlService;
    const command = buildMoveCommand(gql, 'e-1', 'Physik', 1440 + 90); // +1 day +1:30h
    expect(command.label).toBe('„Physik" verschoben');
    command.execute().subscribe();
    expect(mutate.mock.calls[0][1]).toEqual({ ids: ['e-1'], shift: 'PT1530M' });
    command.undo!().subscribe();
    expect(mutate.mock.calls[1][1]).toEqual({ ids: ['e-1'], shift: 'PT-1530M' });
  });
});
