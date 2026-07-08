/**
 * Week time-grid layout (PRD 077/100) — rapla lane model ported from the shared
 * Swing/HTML strategy machinery (`AbstractGroupStrategy` +
 * `GroupAllocatablesStrategy`, see docs/architecture/calendar-rendering.md §2):
 * blocks group by their first SELECTED resource, conflicts within a group open
 * an extra lane right after it, and lanes either stay fixed per resource
 * ('fixed', Swing fixed-slots — stable columns across the week) or merge
 * greedily ('compact', Swing mergeSlots). Pure functions, no DOM.
 */

export interface NamedRef {
  id: string;
  name: string;
  /** True for rooms/locations — the preferred fallback lane key. */
  isLocation?: boolean;
}

export interface LaneOptions<R> {
  /** Scope resources (the calendar's selection) — enables group-by-selected-
   *  resource. Grouping keys ONLY on these (Swing: `getGroupAllocatable`). */
  selected?: NamedRef[];
  /** 'fixed' reserves a lane per selected resource even when empty that day;
   *  'compact' (default) merges non-colliding lanes. */
  mode?: 'fixed' | 'compact';
  /** The block's allocatables (id+name), for the grouping key + fallback. */
  allocsOf?: (r: R) => NamedRef[];
}

export interface DayBlock<R> {
  row: R;
  lane: number;
  /** Minutes since the day's 00:00 (clipped to this day). */
  startMin: number;
  endMin: number;
  /** Continuation markers when a multi-day block is clipped at a day edge. */
  contLeft: boolean;
  contRight: boolean;
}

export interface DayLayout<R> {
  day: string;
  /** Lane count the column renders with (≥ 1 — empty days keep one lane). */
  lanes: number;
  blocks: DayBlock<R>[];
}

export interface WeekLayout<R> {
  days: DayLayout<R>[];
  /** Rendered axis, whole hours; defaults 8–18, expanded to fit the data. */
  startHour: number;
  endHour: number;
}

const DAY_MIN = 24 * 60;
/** Swing `AbstractGroupStrategy.isCollision`: blocks shorter than 5 minutes
 *  still claim 5 minutes of lane space. */
const COLLISION_FLOOR_MIN = 5;

/** 'YYYY-MM-DD' of the Monday of the week containing {@code iso}. */
export function mondayOf(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  const shift = (date.getUTCDay() + 6) % 7; // Mo=0 … So=6
  date.setUTCDate(date.getUTCDate() - shift);
  return date.toISOString().slice(0, 10);
}

function shiftDay(iso: string, n: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
}

/** The 7 days (Mo–So) of the week containing {@code anchorIso}. */
export function weekDays(anchorIso: string): string[] {
  const monday = mondayOf(anchorIso);
  return Array.from({ length: 7 }, (_, i) => shiftDay(monday, i));
}

/** Query window for week mode: Monday 00:00 → next Monday 00:00 (PRD 095 D4 pattern). */
export function weekGridWindow(anchorIso: string): { from: string; to: string } {
  const monday = mondayOf(anchorIso);
  return { from: `${monday}T00:00:00`, to: `${shiftDay(monday, 7)}T00:00:00` };
}

function minutesOf(iso: string): number {
  return Number(iso.slice(11, 13)) * 60 + Number(iso.slice(14, 16));
}

type Seg<R> = Omit<DayBlock<R>, 'lane'>;

function collides<R>(a: Seg<R>, b: Seg<R>): boolean {
  const endA = Math.max(a.endMin, a.startMin + COLLISION_FLOOR_MIN);
  const endB = Math.max(b.endMin, b.startMin + COLLISION_FLOOR_MIN);
  return a.startMin < endB && b.startMin < endA;
}

const byStart = <R>(a: Seg<R>, b: Seg<R>) => a.startMin - b.startMin || b.endMin - a.endMin;

/**
 * Swing `AbstractGroupStrategy.resolveConflicts`: within each group, blocks
 * colliding with an earlier one move to ONE new slot inserted right after the
 * group; the new slot is itself revisited, so cascading conflicts keep opening
 * further lanes.
 */
function resolveConflicts<R>(groups: Seg<R>[][]): void {
  let pos = 0;
  while (pos < groups.length) {
    const group = groups[pos++];
    let newSlot: Seg<R>[] | null = null;
    let i = 0;
    while (i < group.length) {
      const first = group[i++];
      let j = i;
      while (j < group.length) {
        const other = group[j++];
        if (collides(first, other)) {
          group.splice(--j, 1);
          if (!newSlot) {
            newSlot = [];
            groups.splice(pos, 0, newSlot);
          }
          newSlot.push(other);
        }
      }
    }
  }
}

/** Swing `canMerge`: sorted-walk pairwise collision check between two slots. */
function canMerge<R>(slot1: Seg<R>[], slot2: Seg<R>[]): boolean {
  let i = 0;
  let j = 0;
  while (i < slot1.length && j < slot2.length) {
    const b1 = slot1[i];
    const b2 = slot2[j];
    if (collides(b1, b2)) return false;
    if (b1.startMin < b2.startMin) i++;
    else j++;
  }
  return true;
}

/** Swing `mergeSlots`: greedy merge of non-colliding slots (compact mode). */
function mergeSlots<R>(slots: Seg<R>[][]): void {
  let pos = 0;
  while (pos < slots.length) {
    const slot1 = slots[pos++];
    for (let i = pos; i < slots.length; i++) {
      if (canMerge(slot1, slots[i])) {
        slot1.push(...slots[i]);
        slot1.sort(byStart);
        slots.splice(i, 1);
        pos--;
        break;
      }
    }
  }
}

/**
 * Swing `GroupAllocatablesStrategy.group` + `RaplaBuilder.getGroupAllocatable`:
 * one group per SELECTED resource (kept even when empty — fixed-slots lane
 * reservation); a block keys on its first allocatable that is in the selection,
 * falls back to its own allocatables — preferring the LOCATION (room): when the
 * scope chip is a container (building/category) the server expands it for the
 * query but no block carries the chip id, and per-room fallback lanes reproduce
 * Swing's dense per-room columns (a room's sequential lectures share one lane).
 * Blocks with no allocatables land in a trailing group. Groups ordered by
 * locale-collated name.
 */
function groupBySelected<R>(
  segs: Seg<R>[],
  selected: NamedRef[],
  allocsOf: (r: R) => NamedRef[],
): Seg<R>[][] {
  const selectedIds = new Set(selected.map((s) => s.id));
  const groups = new Map<string, { name: string; blocks: Seg<R>[] }>();
  for (const s of selected) groups.set(s.id, { name: s.name, blocks: [] });
  const noAlloc: Seg<R>[] = [];

  for (const seg of segs) {
    const allocs = allocsOf(seg.row);
    const key =
      allocs.find((a) => selectedIds.has(a.id)) ??
      allocs.find((a) => a.isLocation === true) ??
      allocs[0];
    if (!key) {
      noAlloc.push(seg);
      continue;
    }
    let group = groups.get(key.id);
    if (!group) {
      group = { name: key.name, blocks: [] };
      groups.set(key.id, group);
    }
    group.blocks.push(seg);
  }

  const sorted = Array.from(groups.values()).sort((a, b) =>
    a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }),
  );
  const result = sorted.map((g) => g.blocks);
  if (noAlloc.length) result.push(noAlloc);
  return result;
}

/**
 * Lay the rows of one week out into 7 day columns. Multi-day rows are clipped
 * into per-day segments with ‹ › continuation flags; lanes per the strategy
 * pipeline above (see module doc).
 */
export function layoutWeek<R>(
  rows: R[],
  anchorIso: string,
  startOf: (r: R) => string,
  endOf: (r: R) => string,
  options?: LaneOptions<R>,
): WeekLayout<R> {
  const days = weekDays(anchorIso);
  const grouping = !!(options?.selected?.length && options.allocsOf);
  const fixed = options?.mode === 'fixed';
  let minHour = 8;
  let maxHour = 18;

  const perDay = days.map((day) => {
    const segs: Seg<R>[] = [];
    for (const row of rows) {
      const start = startOf(row);
      const end = endOf(row);
      if (!start || !end) continue;
      const startDay = start.slice(0, 10);
      const endDay = end.slice(0, 10);
      if (day < startDay || day > endDay) continue;
      const startMin = startDay === day ? minutesOf(start) : 0;
      let endMin = endDay === day ? minutesOf(end) : DAY_MIN;
      if (endDay === day && endMin === 0 && startDay !== day) continue; // ends at 00:00 → previous day
      if (endMin < startMin) endMin = startMin;
      segs.push({
        row,
        startMin,
        endMin,
        contLeft: startDay < day,
        contRight: endDay > day && !(endDay === shiftDay(day, 1) && end.slice(11) === '00:00:00'),
      });
      minHour = Math.min(minHour, Math.floor(startMin / 60));
      maxHour = Math.max(maxHour, Math.min(24, Math.ceil(Math.max(endMin, startMin + 1) / 60)));
    }
    segs.sort(byStart);

    const groups = grouping
      ? groupBySelected(segs, options!.selected!, options!.allocsOf!)
      : [segs];
    resolveConflicts(groups);
    if (!fixed) mergeSlots(groups);

    const blocks: DayBlock<R>[] = [];
    groups.forEach((group, lane) => {
      for (const seg of group) blocks.push({ ...seg, lane });
    });
    return { day, lanes: Math.max(1, groups.length), blocks };
  });

  return { days: perDay, startHour: minHour, endHour: maxHour };
}
