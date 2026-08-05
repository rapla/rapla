import { shiftIso } from '../actions/event-commands';
import { generateAppointmentId, generateEventId, type EventDraft } from './event-draft';
import type { EventTemplate, NewEventType } from './new-event-options.service';

/** PRD 104 Phase 3 — pure logic of the unified "Neu" picker, tier-5-testable. */

export type PickKind = 'type' | 'template';

/** One selectable row: an event type (`id` = typeKey) or a template (`id` = allocatable id). */
export interface PickItem {
  kind: PickKind;
  id: string;
  name: string;
}

const MAX_RECENTS = 6;

/** Types first (the short list), then the name-sorted templates. */
export function buildPickItems(types: NewEventType[], templates: EventTemplate[]): PickItem[] {
  return [
    ...types.map((t): PickItem => ({ kind: 'type', id: t.key, name: t.name })),
    ...templates.map((t): PickItem => ({ kind: 'template', id: t.id, name: t.name })),
  ];
}

/** AND over whitespace-split terms, case-insensitive substring each. */
export function filterPickItems(items: PickItem[], query: string): PickItem[] {
  const terms = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (!terms.length) return items;
  return items.filter((item) => {
    const hay = item.name.toLowerCase();
    return terms.every((term) => hay.includes(term));
  });
}

/** Newest first, deduplicated by kind+id, capped at {@link MAX_RECENTS}. */
export function pushRecent(recents: PickItem[], picked: PickItem): PickItem[] {
  return [
    { kind: picked.kind, id: picked.id, name: picked.name },
    ...recents.filter((r) => r.kind !== picked.kind || r.id !== picked.id),
  ].slice(0, MAX_RECENTS);
}

/** Optional placement target for a template instantiation (drag-create slot). */
export interface PlacementTarget {
  /** YYYY-MM-DD the template's FIRST appointment day is shifted onto. */
  day: string;
  /** Minutes since midnight for the first appointment's start; null keeps template times. */
  startMin: number | null;
}

const DAY_MINUTES = 24 * 60;

/**
 * PRD 104 Phase 4 (PRD 099 D6) — turn a LOADED template reservation into a NEW
 * draft: every id re-keyed (reservation + appointments, allocation restrictions
 * remapped), nothing persisted, and a TEMPLATE-WIDE date shift aligning the
 * earliest appointment onto {@code target} (whole days; plus minutes when the
 * caller dragged a concrete slot). Repeating end bounds and exceptions shift
 * along — a shifted series keeps its shape.
 */
export function draftFromTemplate(source: EventDraft, target: PlacementTarget | null): EventDraft {
  const idMap = new Map(source.appointments.map((a) => [a.id, generateAppointmentId()]));
  const shiftMinutes = target ? placementShiftMinutes(source, target) : 0;
  const shift = (iso: string) => (shiftMinutes === 0 ? iso : shiftIso(iso, shiftMinutes));
  return {
    id: generateEventId(),
    persisted: false,
    typeKey: source.typeKey,
    values: { ...source.values },
    appointments: source.appointments.map((a) => ({
      id: idMap.get(a.id) ?? generateAppointmentId(),
      start: shift(a.start),
      end: shift(a.end),
      allDay: a.allDay,
      repeating: a.repeating
        ? {
            ...a.repeating,
            end: a.repeating.end ? shift(a.repeating.end) : null,
            exceptions: a.repeating.exceptions.map(shift),
          }
        : null,
    })),
    allocations: source.allocations.map((al) => ({
      ...al,
      appointmentIds: al.appointmentIds
        ? al.appointmentIds.flatMap((id) => {
            const mapped = idMap.get(id);
            return mapped ? [mapped] : [];
          })
        : null,
    })),
    lastChanged: null,
  };
}

function placementShiftMinutes(source: EventDraft, target: PlacementTarget): number {
  const earliest = [...source.appointments.map((a) => a.start)].sort()[0];
  if (!earliest) return 0;
  const sourceDay = earliest.slice(0, 10);
  const dayDelta = Math.round(
    (Date.parse(target.day + 'T00:00:00Z') - Date.parse(sourceDay + 'T00:00:00Z')) / 60000,
  );
  if (target.startMin == null) return dayDelta;
  const sourceMin = Number(earliest.slice(11, 13)) * 60 + Number(earliest.slice(14, 16));
  return dayDelta + (target.startMin % DAY_MINUTES) - sourceMin;
}
