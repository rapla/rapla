import { describe, expect, it } from 'vitest';
import { DraftHistory, type DraftContent } from './draft-history';
import { newDraft, snapshot, toReservationInput } from './event-draft';

function content(name: string, appointmentCount = 1): DraftContent {
  return {
    typeKey: 'event',
    values: { name },
    appointments: Array.from({ length: appointmentCount }, (_, i) => ({
      id: `a-${i}`,
      start: '2026-07-07T10:00:00',
      end: '2026-07-07T11:00:00',
      allDay: false,
      repeating: null,
    })),
    allocations: [],
  };
}

describe('DraftHistory (PRD 091 D5 memento stack)', () => {
  it('push → undo restores the pre-mutation state; redo returns forward', () => {
    const h = new DraftHistory();
    h.push(content('v1'), 'Name', 'values:name', 0);
    const restored = h.undo(content('v2'));
    expect(restored?.values['name']).toBe('v1');
    expect(h.canUndo()).toBe(false);
    expect(h.canRedo()).toBe(true);
    const redone = h.redo(content('v1'));
    expect(redone?.values['name']).toBe('v2');
    expect(h.canUndo()).toBe(true);
    expect(h.canRedo()).toBe(false);
  });

  it('undo/redo on empty stacks return null', () => {
    const h = new DraftHistory();
    expect(h.undo(content('x'))).toBeNull();
    expect(h.redo(content('x'))).toBeNull();
  });

  it('coalesces same key within 1 s into ONE entry (keeps the oldest snapshot)', () => {
    const h = new DraftHistory();
    h.push(content('K'), 'Name', 'values:name', 0);
    h.push(content('Kl'), 'Name', 'values:name', 400);
    h.push(content('Kla'), 'Name', 'values:name', 800);
    expect(h.depth()).toBe(1);
    expect(h.undo(content('Klausur'))?.values['name']).toBe('K');
  });

  it('coalesce window SLIDES with each merge', () => {
    const h = new DraftHistory();
    h.push(content('a'), 'Name', 'values:name', 0);
    h.push(content('ab'), 'Name', 'values:name', 900);
    h.push(content('abc'), 'Name', 'values:name', 1800); // 900ms after previous
    expect(h.depth()).toBe(1);
  });

  it('does NOT coalesce after a >1 s pause, across keys, or with a null key', () => {
    const h = new DraftHistory();
    h.push(content('a'), 'Name', 'values:name', 0);
    h.push(content('b'), 'Name', 'values:name', 1500);
    expect(h.depth()).toBe(2);
    h.push(content('c'), 'Beginn', 'appt:a-0:start', 1600);
    expect(h.depth()).toBe(3);
    h.push(content('d'), '+ Termin', null, 1700);
    h.push(content('e'), '+ Termin', null, 1750);
    expect(h.depth()).toBe(5);
  });

  it('a new push clears the redo stack', () => {
    const h = new DraftHistory();
    h.push(content('v1'), 'Name', null, 0);
    h.undo(content('v2'));
    expect(h.canRedo()).toBe(true);
    h.push(content('v1b'), 'Name', null, 5000);
    expect(h.canRedo()).toBe(false);
  });

  it('caps at 50 entries, evicting the oldest', () => {
    const h = new DraftHistory();
    for (let i = 0; i < 60; i++) h.push(content(`v${i}`), 'Name', null, i * 5000);
    expect(h.depth()).toBe(50);
    let last: DraftContent | null = null;
    let cur = content('current');
    for (let i = 0; i < 50; i++) {
      last = h.undo(cur);
      cur = last!;
    }
    expect(last?.values['name']).toBe('v10');
    expect(h.canUndo()).toBe(false);
  });

  it('clone isolation: mutating the pushed object or the returned one never corrupts history', () => {
    const h = new DraftHistory();
    const original = content('pristine');
    h.push(original, 'Name', null, 0);
    original.values['name'] = 'mutated-after-push';
    original.appointments[0].start = '2030-01-01T00:00:00';
    const restored = h.undo(content('now'))!;
    expect(restored.values['name']).toBe('pristine');
    expect(restored.appointments[0].start).toBe('2026-07-07T10:00:00');
    restored.values['name'] = 'mutated-restored';
    // the future entry captured 'now' at undo time — mutating the returned
    // clone afterwards must not have corrupted it
    const redone = h.redo(restored)!;
    expect(redone.values['name']).toBe('now');
    // and the past entry captured at redo time keeps the value from that moment
    expect(h.undo(redone)?.values['name']).toBe('mutated-restored');
  });

  it('labels are exposed for tooltips', () => {
    const h = new DraftHistory();
    h.push(content('v1'), 'Termin gelöscht', null, 0);
    expect(h.undoLabel()).toBe('Termin gelöscht');
    h.undo(content('v2'));
    expect(h.redoLabel()).toBe('Termin gelöscht');
  });

  it('clear() empties both stacks', () => {
    const h = new DraftHistory();
    h.push(content('v1'), 'Name', null, 0);
    h.undo(content('v2'));
    h.push(content('v3'), 'Name', null, 5000);
    h.clear();
    expect(h.canUndo()).toBe(false);
    expect(h.canRedo()).toBe(false);
    expect(h.depth()).toBe(0);
  });

  it('undo back to the baseline makes the draft compare clean (dirty off)', () => {
    const draft = newDraft('event', new Date(2026, 6, 7, 9, 30), 'e-base');
    const baseline = snapshot(draft);
    const h = new DraftHistory();
    const before: DraftContent = toReservationInputContent(draft);
    h.push(before, 'Name', 'values:name', 0);
    draft.values['name'] = 'geändert';
    const restored = h.undo(toReservationInputContent(draft))!;
    Object.assign(draft, restored);
    expect(snapshot(draft)).toBe(baseline);
  });
});

/** Sheet-side projection: the D5 snapshot boundary = the toReservationInput fields. */
function toReservationInputContent(d: ReturnType<typeof newDraft>): DraftContent {
  void toReservationInput(d); // documents the boundary the content mirrors
  return {
    typeKey: d.typeKey,
    values: d.values,
    appointments: d.appointments,
    allocations: d.allocations,
  };
}
