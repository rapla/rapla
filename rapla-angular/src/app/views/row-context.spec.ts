import { describe, expect, it } from 'vitest';

import { extractRowContext } from './row-context';
import type { ViewColumn } from '../graphql/graphql.service';

const ALLOC_COLUMNS: ViewColumn[] = [
  { alias: 'name', header: 'Titel', type: 'String', order: 1 },
  { alias: 'personen', header: 'Personen', type: 'Resource', order: 2 },
  { alias: 'raum', header: 'Raum', type: 'Resource', order: 3 },
];

describe('extractRowContext (PRD 094 D4)', () => {
  it('reservation object subject → primary reservation with canModify', () => {
    const ctx = extractRowContext(
      { name: 'Physik', reservation: { id: 'e1', canModify: true } },
      'Termine',
    );
    expect(ctx.primary).toEqual({ kind: 'reservation', id: 'e1', canModify: true });
    expect(ctx.viewName).toBe('Termine');
    expect(ctx.rows.length).toBe(1);
  });

  // PRD 111 D3 — the document menu maps subject kind + typeKey → the type's `documents`.
  it('carries typeKey from the subject classification when the view selects it', () => {
    const ctx = extractRowContext(
      { reservation: { id: 'e1', canModify: true, classification: { typeKey: 'ausleihe' } } },
      'v',
    );
    expect(ctx.primary).toEqual({
      kind: 'reservation',
      id: 'e1',
      canModify: true,
      typeKey: 'ausleihe',
    });
  });

  // Fail closed: a stored view that does not select the key simply offers no document entries.
  it('leaves typeKey undefined when the view does not select the classification', () => {
    const ctx = extractRowContext({ reservation: { id: 'e1' } }, 'v');
    expect(ctx.primary?.typeKey).toBeUndefined();
  });

  it('scalar fallback carries typeKey too', () => {
    const ctx = extractRowContext(
      { reservationId: 'e2', canModify: false, classification: { typeKey: 'ausleihe' } },
      'v',
    );
    expect(ctx.primary?.typeKey).toBe('ausleihe');
  });

  it('scalar fallback: reservationId + canModify (reservations-root views)', () => {
    const ctx = extractRowContext({ reservationId: 'e2', canModify: false }, 'v');
    expect(ctx.primary).toEqual({ kind: 'reservation', id: 'e2', canModify: false });
  });

  it('resource subject → primary resource', () => {
    const ctx = extractRowContext({ resource: { id: 'a9', canModify: true } }, 'v');
    expect(ctx.primary).toEqual({ kind: 'resource', id: 'a9', canModify: true });
  });

  it('user subject → primary user (no canModify)', () => {
    const ctx = extractRowContext({ user: { id: 'u3' } }, 'v');
    expect(ctx.primary).toEqual({ kind: 'user', id: 'u3', canModify: undefined });
  });

  it('priority order: reservation wins over resource/user', () => {
    const ctx = extractRowContext(
      { user: { id: 'u1' }, resource: { id: 'a1' }, reservation: { id: 'e1' } },
      'v',
    );
    expect(ctx.primary?.kind).toBe('reservation');
  });

  it('no subject field → primary null (aggregate rows stay menu-free)', () => {
    const ctx = extractRowContext({ name: 'Summe', count: 12 }, 'v');
    expect(ctx.primary).toBeNull();
    expect(ctx.entities).toEqual([]);
  });

  it('secondary entities: Resource-typed columns contribute their cell ids', () => {
    const row = {
      name: 'Physik',
      reservation: { id: 'e1', canModify: true },
      personen: [{ id: 'p1', name: 'Prof X' }],
      raum: [
        { id: 'r1', name: 'C452' },
        { id: 'r2', name: 'C348' },
      ],
    };
    const ctx = extractRowContext(row, 'v', ALLOC_COLUMNS);
    expect(ctx.primary?.id).toBe('e1');
    const allocIds = ctx.entities.filter((e) => e.kind === 'resource').map((e) => e.id);
    expect(allocIds).toEqual(['p1', 'r1', 'r2']);
    expect(ctx.entities[0]).toEqual({ kind: 'reservation', id: 'e1', canModify: true });
  });

  it('malformed subject (no string id) is ignored', () => {
    const ctx = extractRowContext({ reservation: { canModify: true } }, 'v');
    expect(ctx.primary).toBeNull();
  });
});

describe('block identity sourcing (PRD 095 Phase 3b)', () => {
  it('appointment object subject → block.appointmentId', () => {
    const ctx = extractRowContext(
      { reservation: { id: 'e1' }, appointment: { id: 'a7', repeating: null } },
      'v',
    );
    expect(ctx.block.appointmentId).toBe('a7');
  });

  it('legacy scalar appointmentId still sources the block (old stored views)', () => {
    const ctx = extractRowContext({ reservation: { id: 'e1' }, appointmentId: 'a8' }, 'v');
    expect(ctx.block.appointmentId).toBe('a8');
  });

  it('appointment object wins over the legacy scalar when both are selected', () => {
    const ctx = extractRowContext(
      { reservation: { id: 'e1' }, appointment: { id: 'a-new' }, appointmentId: 'a-old' },
      'v',
    );
    expect(ctx.block.appointmentId).toBe('a-new');
  });

  it('malformed appointment object (no string id) falls back to the scalar', () => {
    const ctx = extractRowContext(
      { reservation: { id: 'e1' }, appointment: { repeating: null }, appointmentId: 'a9' },
      'v',
    );
    expect(ctx.block.appointmentId).toBe('a9');
  });
});
