import { describe, expect, it } from 'vitest';

import { extractRowContext } from './row-context';
import type { ViewColumn } from '../graphql/graphql.service';

const ALLOC_COLUMNS: ViewColumn[] = [
  { alias: 'name', header: 'Titel', type: 'String', order: 1 },
  { alias: 'personen', header: 'Personen', type: 'Allocatable', order: 2 },
  { alias: 'raum', header: 'Raum', type: 'Allocatable', order: 3 },
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

  it('scalar fallback: reservationId + canModify (reservations-root views)', () => {
    const ctx = extractRowContext({ reservationId: 'e2', canModify: false }, 'v');
    expect(ctx.primary).toEqual({ kind: 'reservation', id: 'e2', canModify: false });
  });

  it('allocatable subject → primary allocatable', () => {
    const ctx = extractRowContext({ allocatable: { id: 'a9', canModify: true } }, 'v');
    expect(ctx.primary).toEqual({ kind: 'allocatable', id: 'a9', canModify: true });
  });

  it('user subject → primary user (no canModify)', () => {
    const ctx = extractRowContext({ user: { id: 'u3' } }, 'v');
    expect(ctx.primary).toEqual({ kind: 'user', id: 'u3', canModify: undefined });
  });

  it('priority order: reservation wins over allocatable/user', () => {
    const ctx = extractRowContext(
      { user: { id: 'u1' }, allocatable: { id: 'a1' }, reservation: { id: 'e1' } },
      'v',
    );
    expect(ctx.primary?.kind).toBe('reservation');
  });

  it('no subject field → primary null (aggregate rows stay menu-free)', () => {
    const ctx = extractRowContext({ name: 'Summe', count: 12 }, 'v');
    expect(ctx.primary).toBeNull();
    expect(ctx.entities).toEqual([]);
  });

  it('secondary entities: Allocatable-typed columns contribute their cell ids', () => {
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
    const allocIds = ctx.entities.filter((e) => e.kind === 'allocatable').map((e) => e.id);
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
