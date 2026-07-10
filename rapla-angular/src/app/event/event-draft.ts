/**
 * PRD 091 Phase 2.2 — the event sheet's draft model. Pure TS, no Angular.
 *
 * Round-trip safety (plan step 2.0b): the draft carries the COMPLETE loaded
 * state — classification values the UI doesn't render and repeating rules the
 * UI can't edit yet ride along opaquely and are echoed verbatim into the
 * full-state mutation input. The UI edits an overlay of known fields only.
 *
 * Id-first (D3 / PRD 056 §9): drafts mint their own entity ids at creation —
 * rapla-typed UUIDs ('e…' reservation, 'a…' appointment) so the route URL is
 * the permanent event URL and allocation restrictions can join on appointment
 * ids before anything is persisted.
 */

export interface RepeatingRule {
  type: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
  interval: number;
  end: string | null;
  count: number | null;
  weekdays: number[] | null;
  exceptions: string[];
}

export interface DraftAppointment {
  id: string;
  /** ISO LocalDateTime, e.g. 2026-07-07T12:00:00 */
  start: string;
  end: string;
  allDay: boolean;
  /** Pass-through — the slice displays but never edits this. */
  repeating: RepeatingRule | null;
}

export interface DraftAllocation {
  allocatableId: string;
  /** Display only; not part of the mutation input. */
  allocatableName: string;
  /** null = applies to ALL appointments (empty restriction). */
  appointmentIds: string[] | null;
}

export interface EventDraft {
  id: string;
  /** false = brand-new (id not persisted yet) — save uses createReservation. */
  persisted: boolean;
  typeKey: string;
  /** COMPLETE classification values keyed by attribute key (pass-through). */
  values: Record<string, unknown>;
  appointments: DraftAppointment[];
  allocations: DraftAllocation[];
  /** Concurrency token for updateReservation. */
  lastChanged: string | null;
}

/** rapla id convention: UUID v4 with the first char replaced by the type letter. */
function typedId(prefix: 'e' | 'a'): string {
  return prefix + crypto.randomUUID().substring(1);
}

export const generateEventId = (): string => typedId('e');
export const generateAppointmentId = (): string => typedId('a');

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

function localIso(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
}

/** Fresh draft with one default appointment (next full hour, 1 h long). */
export function newDraft(typeKey: string, now: Date, id: string = generateEventId()): EventDraft {
  const start = new Date(now);
  start.setMinutes(0, 0, 0);
  start.setHours(start.getHours() + 1);
  const end = new Date(start);
  end.setHours(end.getHours() + 1);
  return {
    id,
    persisted: false,
    typeKey,
    values: {},
    appointments: [
      {
        id: generateAppointmentId(),
        start: localIso(start),
        end: localIso(end),
        allDay: false,
        repeating: null,
      },
    ],
    allocations: [],
    lastChanged: null,
  };
}

/** Minimal shape of a view scope chip (structurally a `FilterStore` entry). */
export interface ScopeChip {
  id: string;
  kind: string;
  label: string;
}

/**
 * Swing parity — the view's selected RESOURCE scope chips become allocations of
 * a new event (applies-to-all, null restriction). A `user` chip is an owner
 * filter, not an allocatable, and is skipped. Shared by every "new event from a
 * scoped view" entry point (toolbar "Neu" today, the quick-create window once
 * the month view lands) so the pre-allocation logic lives in ONE place.
 */
export function scopeAllocations(chips: ScopeChip[]): DraftAllocation[] {
  return chips
    .filter((c) => c.kind === 'resource')
    .map((c) => ({ allocatableId: c.id, allocatableName: c.label, appointmentIds: null }));
}

/** A new draft pre-seeded with the view scope's resources (see {@link scopeAllocations}). */
export function newScopedDraft(
  typeKey: string,
  now: Date,
  chips: ScopeChip[],
  id: string = generateEventId(),
): EventDraft {
  const draft = newDraft(typeKey, now, id);
  draft.allocations = scopeAllocations(chips);
  return draft;
}

/**
 * PRD 095 Phase 3 — a scoped draft seeded from a month-grid day-range selection
 * (drag-create). Single day → the newDraft-conventional one-hour slot at 09:00;
 * a multi-day range spans first day 09:00 → last day 17:00. Days are
 * 'YYYY-MM-DD', {@code toDay} inclusive.
 */
export function rangeScopedDraft(
  typeKey: string,
  chips: ScopeChip[],
  fromDay: string,
  toDay: string,
  id: string = generateEventId(),
): EventDraft {
  const draft = newScopedDraft(typeKey, new Date(), chips, id);
  const single = fromDay === toDay;
  draft.appointments[0].start = `${fromDay}T09:00:00`;
  draft.appointments[0].end = single ? `${fromDay}T10:00:00` : `${toDay}T17:00:00`;
  return draft;
}

/**
 * PRD 077 week grid — a scoped draft seeded from a time-range selection
 * (drag-create): minute-precise start/end, already snapped to the grid's slot
 * raster by the caller. A cross-day selection (Swing SelectionHandler FLOW)
 * becomes ONE appointment spanning fromDay/start → toDay/end.
 */
export function timeScopedDraft(
  typeKey: string,
  chips: ScopeChip[],
  fromDay: string,
  startMin: number,
  toDay: string,
  endMin: number,
  id: string = generateEventId(),
): EventDraft {
  const draft = newScopedDraft(typeKey, new Date(), chips, id);
  const hhmm = (min: number) =>
    `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}:00`;
  draft.appointments[0].start = `${fromDay}T${hhmm(startMin)}`;
  draft.appointments[0].end = `${toDay}T${hhmm(Math.min(endMin, 24 * 60 - 1))}`;
  return draft;
}

/**
 * Four-field coupling (Swing parity, locked in the 2026-07-07 prototype
 * round): editing the START — date or time — SHIFTS the end so the duration
 * stays; editing the END changes the duration but never crosses the start
 * (clamped to start + 15 min). Both operate on ISO LocalDateTime strings.
 */
export function withStart(
  a: { start: string; end: string },
  newStart: string,
): { start: string; end: string } {
  const duration = new Date(a.end).getTime() - new Date(a.start).getTime();
  return { start: newStart, end: localIso(new Date(new Date(newStart).getTime() + duration)) };
}

export function withEnd(
  a: { start: string; end: string },
  newEnd: string,
): { start: string; end: string } {
  const start = new Date(a.start).getTime();
  const end =
    new Date(newEnd).getTime() <= start ? localIso(new Date(start + 15 * 60_000)) : newEnd;
  return { start: a.start, end };
}

/** Raw wire shape of the sheet's load query (see event-data.service). */
export interface ReservationWire {
  id: string;
  lastChanged?: string | null;
  classification: { typeKey: string } & Record<string, unknown>;
  appointments: {
    id: string;
    start: string;
    end: string;
    allDay: boolean;
    repeating: RepeatingRule | null;
  }[];
  allocations: {
    allocatable: { id: string; name?: string | null };
    appointmentIds: string[] | null;
  }[];
}

/** Wire → draft. Classification values = every key except the meta fields. */
export function fromReservation(wire: ReservationWire): EventDraft {
  const values: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(wire.classification)) {
    if (k !== 'typeKey' && k !== 'type' && k !== '__typename') values[k] = v;
  }
  return {
    id: wire.id,
    persisted: true,
    typeKey: wire.classification.typeKey,
    values,
    appointments: wire.appointments.map((a) => ({
      id: a.id,
      start: a.start,
      end: a.end,
      allDay: a.allDay,
      repeating: a.repeating ? { ...a.repeating } : null,
    })),
    allocations: wire.allocations.map((al) => ({
      allocatableId: al.allocatable.id,
      allocatableName: al.allocatable.name ?? al.allocatable.id,
      appointmentIds: al.appointmentIds ? [...al.appointmentIds] : null,
    })),
    lastChanged: wire.lastChanged ?? null,
  };
}

interface AppointmentInput {
  id: string;
  start: string;
  end: string;
  allDay: boolean;
  repeating?: RepeatingRule;
}

interface AllocationInput {
  allocatableId: string;
  appointmentIds?: string[];
}

export interface ReservationInput {
  typeKey: string;
  classification: Record<string, Record<string, unknown>>;
  appointments: AppointmentInput[];
  allocations: AllocationInput[];
}

/**
 * Draft → full-state mutation input (shared by create and update; create adds
 * `id`, update adds `expectedLastChanged` at the call site). Unknown
 * classification values and untouched repeating rules are echoed verbatim —
 * the 2.0b round-trip invariant.
 */
export function toReservationInput(draft: EventDraft): ReservationInput {
  return {
    typeKey: draft.typeKey,
    classification: { [draft.typeKey]: { ...draft.values } },
    appointments: draft.appointments.map((a) => {
      const input: AppointmentInput = { id: a.id, start: a.start, end: a.end, allDay: a.allDay };
      if (a.repeating) {
        input.repeating = {
          ...a.repeating,
          exceptions: [...a.repeating.exceptions],
          weekdays: a.repeating.weekdays ? [...a.repeating.weekdays] : null,
        };
      }
      return input;
    }),
    allocations: draft.allocations.map((al) => {
      const input: AllocationInput = { allocatableId: al.allocatableId };
      if (al.appointmentIds) input.appointmentIds = [...al.appointmentIds];
      return input;
    }),
  };
}

/** Stable serialization for dirty tracking (UC-E14: abort without trace). */
export function snapshot(draft: EventDraft): string {
  return JSON.stringify(toReservationInput(draft));
}

export function isDirty(draft: EventDraft, baseline: string): boolean {
  return snapshot(draft) !== baseline;
}
