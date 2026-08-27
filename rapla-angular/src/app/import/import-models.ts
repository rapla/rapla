/**
 * PRD 104 v2 / dhbwrapla PRD 004 — Dualis import worklist models (pure TS).
 *
 * Mirrors the EXPECTED shape of the H3 read API (`externalEventWorklist`).
 * The wire query lives in {@link ImportWorklistService} — adapt BOTH when the
 * server lands; everything below is server-independent and tier-5-tested.
 */

export type StagedState = 'OPEN' | 'BOUND' | 'IGNORED';

export interface WorklistGroup {
  /** Allocatable id of the Kurs/Studiengang the group hangs on. */
  id: string;
  name: string;
  scope: 'KURS' | 'STUDIENGANG';
  openCount: number;
  boundCount: number;
  changedCount: number;
}

export interface WorklistItem {
  sourceId: string;
  kind: 'v' | 'p';
  name: string;
  unit: string | null;
  semester: string;
  state: StagedState;
  changed: boolean;
  changedSince: string | null;
  groupIds: string[];
  groupName: string;
  /** Reservation the item is bound to (LINKED/CHANGED); null until the server field lands. */
  boundReservationId: string | null;
  /** Raw (unbereinigt) source name — display-only disambiguator in the dialog lists. */
  fullName: string | null;
}

export interface Worklist {
  groups: WorklistGroup[];
  items: WorklistItem[];
}

/** Server-resolved default templates (PRD 104 v3): the naming-convention matching
 *  lives in the DEPLOYMENT (dhbwrapla DualisDefaultTemplateResolver), not in the SPA —
 *  the client only displays what `externalEventDefaultTemplates` returns. */
export interface DefaultTemplates {
  lectureTemplateId?: string | null;
  lectureTemplateName?: string | null;
  examTemplateId?: string | null;
  examTemplateName?: string | null;
}

/** A stamped (externally bound) reservation — the durable "Verknüpft" source
 *  (2026-08-11 redesign): read from the reservations' `externalid` annotation,
 *  NOT from the volatile Halde rows, so bindings stay visible after their
 *  source row left the export window. */
export interface LinkedEvent {
  id: string;
  name: string;
  externalId: string;
  /** ISO LocalDateTime of the first appointment — drives the semester display. */
  firstDate: string;
  allocatableIds: string[];
}

/** LinkedEvents allocated to any of the given groups, deduped, name-sorted. */
export function linkedOfGroups(events: LinkedEvent[], groupIds: string[]): LinkedEvent[] {
  const ids = new Set(groupIds);
  const seen = new Set<string>();
  return events
    .filter((e) => {
      if (!e.allocatableIds.some((a) => ids.has(a)) || seen.has(e.id)) return false;
      seen.add(e.id);
      return true;
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'de'));
}

/** A bind proposal for an OPEN item (server-ranked, NEVER auto-matched). */
export interface BindCandidate {
  reservationId: string;
  name: string;
  firstDate: string | null;
  score: number;
}

/** Minimal draft slice the parked-drop flow touches (structurally EventDraft). */
interface DraftLike {
  allocations: {
    allocatableId: string;
    allocatableName: string;
    appointmentIds: string[] | null;
  }[];
}

/** Parked drop → editor draft: every Kurs group of the staged item becomes an
 *  applies-to-all allocation (never duplicating what the template brought). */
export function draftWithGroups<T extends DraftLike>(
  draft: T,
  groups: { id: string; name: string }[],
): T {
  const known = new Set(draft.allocations.map((a) => a.allocatableId));
  const added = groups
    .filter((g) => !known.has(g.id))
    .map((g) => ({ allocatableId: g.id, allocatableName: g.name, appointmentIds: null }));
  return added.length === 0 ? draft : { ...draft, allocations: [...draft.allocations, ...added] };
}

/** Items with a pending "geändert" marker, deduped, name-sorted. */
export function changedItems(items: WorklistItem[]): WorklistItem[] {
  const seen = new Set<string>();
  return items
    .filter((i) => {
      if (!i.changed || seen.has(i.sourceId)) return false;
      seen.add(i.sourceId);
      return true;
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'de'));
}

/** INTERIM date→semester rule (Apr–Sep = "SoSe YYYY", Oct–Mar = "WiSe YYYY/YY+1").
 *  Semester boundaries differ per Studiengang — the real resolution is a DEPLOYMENT
 *  heuristic behind the read API (PRD 104 § mutations contract, `date` argument);
 *  this client rule only bridges until that lands. */
export function currentSemester(now: Date): string {
  const y = now.getFullYear();
  const m = now.getMonth() + 1;
  if (m >= 4 && m <= 9) return `SoSe ${y}`;
  const start = m >= 10 ? y : y - 1;
  return `WiSe ${start}/${String((start + 1) % 100).padStart(2, '0')}`;
}

/** The date the visible window's semester derives from: its MIDPOINT. A week
 *  straddling a semester boundary (e.g. Mon 29.09. … Mon 06.10.) must count as
 *  the semester of the days it mostly shows — deriving from `from` flipped the
 *  whole sync context to the OLD semester right after jumping to an event at
 *  the boundary (bug 2026-08-12). */
export function windowSemesterDate(w: { from: string; to: string }): Date {
  return new Date((Date.parse(w.from) + Date.parse(w.to)) / 2);
}

/** The date range of the semester containing `now` (same INTERIM rule as
 *  {@link currentSemester}): SoSe = Apr 1 – Oct 1, WiSe = Oct 1 – Apr 1.
 *  ISO LocalDateTime, end exclusive — the window for the linked-reservations
 *  query, so list/badge/dismiss all share the dialog's semester scope. */
export function semesterRange(now: Date): { from: string; to: string } {
  const y = now.getFullYear();
  const m = now.getMonth() + 1;
  if (m >= 4 && m <= 9) return { from: `${y}-04-01T00:00:00`, to: `${y}-10-01T00:00:00` };
  const start = m >= 10 ? y : y - 1;
  return { from: `${start}-10-01T00:00:00`, to: `${start + 1}-04-01T00:00:00` };
}

/** The Sync dialog's list: OPEN items of the given groups, deduped, name-sorted.
 *  `semester` narrows to one scope (the worklist itself loads UNSCOPED for join
 *  safety — see ImportWorklistService); omit for all semesters. */
export function openItemsOfGroups(
  items: WorklistItem[],
  groupIds: string[],
  semester?: string,
): WorklistItem[] {
  const ids = new Set(groupIds);
  const seen = new Set<string>();
  return items
    .filter((i) => {
      if (i.state !== 'OPEN' || !i.groupIds.some((g) => ids.has(g)) || seen.has(i.sourceId))
        return false;
      if (semester && i.semester !== semester) return false;
      seen.add(i.sourceId);
      return true;
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'de'));
}
