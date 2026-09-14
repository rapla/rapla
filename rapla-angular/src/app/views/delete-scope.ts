import type { EventDraft } from '../event/event-draft';

/**
 * PRD 094 Phase 2 — the Swing delete-scope logic, ported from
 * `ReservationControllerImpl.showDialog/deleteAppointment` (see
 * docs/architecture/reservation-edit.md § "Delete — the scope dialog and its
 * cascades"). Pure functions over the loaded draft + the clicked block.
 *
 * Conscious v1 deviation (documented in PRD 094): Swing's empty-series check
 * (`isNotEmptyWithExceptions` — remove the appointment instead of adding an
 * exception that would empty the series) needs occurrence expansion and is
 * skipped; an all-excepted appointment is degenerate but harmless.
 */
export type DeleteScope = 'event' | 'serie' | 'single';

export interface BlockRef {
  appointmentId: string | null;
  /** ISO LocalDateTime of the clicked block occurrence. */
  start: string | null;
  isException: boolean;
}

export interface DeleteScopeOption {
  scope: DeleteScope;
  label: string;
}

function formatDay(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-');
  return `${d}.${m}.${y}`;
}

export function deleteScopeOptions(draft: EventDraft, block: BlockRef): DeleteScopeOption[] {
  const appointment = block.appointmentId
    ? (draft.appointments.find((a) => a.id === block.appointmentId) ?? null)
    : null;
  const multi = draft.appointments.length > 1;

  const options: DeleteScopeOption[] = [{ scope: 'event', label: 'Ganze Veranstaltung' }];
  if (!appointment) return options;

  if (appointment.repeating !== null && multi) {
    options.push({ scope: 'serie', label: 'Serie (alle Termine dieser Wiederholung)' });
  }
  if (appointment.repeating !== null || multi) {
    const day = block.start ? ` (${formatDay(block.start)})` : '';
    options.push({ scope: 'single', label: `Nur dieser Termin${day}` });
  }
  return options;
}

export type DeleteAction = { kind: 'deleteEvent' } | { kind: 'update'; draft: EventDraft };

/** Removes the appointment and its restriction references; a restriction that
 *  becomes empty drops the whole allocation (the resource has no target left). */
function removeAppointment(draft: EventDraft, appointmentId: string): DeleteAction {
  draft.appointments = draft.appointments.filter((a) => a.id !== appointmentId);
  if (draft.appointments.length === 0) return { kind: 'deleteEvent' };
  draft.allocations = draft.allocations
    .map((alloc) =>
      alloc.appointmentIds === null
        ? alloc
        : { ...alloc, appointmentIds: alloc.appointmentIds.filter((id) => id !== appointmentId) },
    )
    .filter((alloc) => alloc.appointmentIds === null || alloc.appointmentIds.length > 0);
  return { kind: 'update', draft };
}

export function applyDeleteScope(
  original: EventDraft,
  scope: DeleteScope,
  block: BlockRef,
): DeleteAction {
  if (scope === 'event') return { kind: 'deleteEvent' };
  const draft = structuredClone(original);
  const appointmentId = block.appointmentId;
  if (!appointmentId) return { kind: 'deleteEvent' };

  if (scope === 'serie') return removeAppointment(draft, appointmentId);

  const appointment = draft.appointments.find((a) => a.id === appointmentId);
  if (!appointment) return { kind: 'deleteEvent' };
  if (appointment.repeating === null) return removeAppointment(draft, appointmentId);

  // Swing: exception dates are DAY-truncated (DateTools.cutDate).
  const exception = block.start ? block.start.slice(0, 10) + 'T00:00:00' : null;
  if (exception && !appointment.repeating.exceptions.includes(exception)) {
    appointment.repeating.exceptions.push(exception);
  }
  return { kind: 'update', draft };
}
