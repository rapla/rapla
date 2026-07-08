import { Component, computed, input, output, signal } from '@angular/core';

import { CHIP_BASE_CSS, chipColor, chipName, chipTime, isMovableRow } from './block-style';
import { layoutWeek, weekDays, type DayBlock, type DayLayout, type NamedRef } from './week-lanes';

type Row = Record<string, unknown>;
type DayBlockView = DayBlock<Row>;

const HOUR_PX = 48;
const DAY_MIN_MAX = 24 * 60;
/** Minimum lane width in px (Swing parity: SwingWeekView floors the slot width
 *  at minBlockWidth and lets the calendar scroll horizontally instead of
 *  squeezing lanes into the viewport). */
const MIN_LANE_PX = 80;

/** Calendar days from {@code a} to {@code b} ('YYYY-MM-DD'), negative when b < a. */
function daysBetween(a: string, b: string): number {
  return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86400000);
}

function localToday(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** The block's allocatable refs for the lane grouping key: scan the row's
 *  array-valued cells (persons/resources aliases in the builtin view; custom
 *  views may alias differently) for {id, name} objects, carrying isLocation
 *  through (the room is the preferred fallback lane key — see week-lanes).
 *  No refs → the block lands in the trailing no-allocatable lane (fail-closed). */
function rowAllocRefs(row: Row): NamedRef[] {
  const refs: NamedRef[] = [];
  for (const value of Object.values(row)) {
    if (!Array.isArray(value)) continue;
    for (const item of value) {
      const o = item as Record<string, unknown> | null;
      if (o && typeof o['id'] === 'string' && typeof o['name'] === 'string') {
        refs.push({ id: o['id'], name: o['name'], isLocation: o['isLocation'] === true });
      }
    }
  }
  return refs;
}

/**
 * Week time-grid (PRD 077 prototype). 7 day columns Mo–So over an hour axis;
 * overlapping blocks open dynamic LANES and the day column widens with them
 * (rapla Swing/HTML week-view slot model); visual language oriented on
 * Google Calendar / EventCalendar (time gutter, colored chips, today marker).
 * Layout math lives in {@code week-lanes.ts}; this component only renders.
 */
@Component({
  selector: 'app-week-grid',
  template: `
    <div class="wg">
      <div class="hdr" [style.gridTemplateColumns]="gridCols()">
        <div class="gutter">
          <select
            class="raster"
            title="Zeitraster"
            [value]="rowsPerHour()"
            (change)="onRaster($event)"
          >
            <option value="1">1h</option>
            <option value="2">30m</option>
            <option value="4">15m</option>
          </select>
        </div>
        @for (d of layout().days; track d.day) {
          <div class="dayhdr" [class.today]="d.day === today">
            <span class="dow">{{ dowOf(d.day) }}</span>
            <span class="dnum">{{ dayNum(d.day) }}</span>
          </div>
        }
      </div>
      <div class="body" [style.gridTemplateColumns]="gridCols()">
        <div class="gutter times" [style.height.px]="bodyHeight()">
          @for (h of hours(); track h) {
            <span class="hlabel" [style.top.px]="hourTop(h)">{{ h }}:00</span>
          }
        </div>
        @for (d of layout().days; track d.day) {
          <div
            class="daycol"
            [class.today]="d.day === today"
            [style.height.px]="bodyHeight()"
            [attr.data-day]="d.day"
            (pointerdown)="armCreate($event, d.day)"
          >
            @for (h of hours(); track h) {
              <div class="hline" [style.top.px]="hourTop(h)"></div>
              @for (sub of subSlots(); track sub) {
                <div class="subline" [style.top.px]="hourTop(h) + sub"></div>
              }
            }
            @if (d.day === today) {
              <div class="now" [style.top.px]="nowTop()"></div>
            }
            @if (selSegment(d.day); as seg) {
              <div
                class="createsel"
                [style.top.px]="blockTop(seg.startMin)"
                [style.height.px]="blockHeight(seg.startMin, seg.endMin)"
              >
                {{ seg.label }}
              </div>
            }
            @if (movePreview(); as mp) {
              @if (mp.day === d.day) {
                <!-- the dragged block itself travels: same color/name, new time -->
                <span
                  class="chip ghost"
                  [class.neutral]="!color(mp.row)"
                  [style.background]="color(mp.row)"
                  [style.top.px]="blockTop(mp.startMin)"
                  [style.height.px]="blockHeight(mp.startMin, mp.endMin)"
                >
                  <span class="t">{{ selLabelOf(mp.startMin, mp.endMin) }}</span>
                  <span class="n">{{ nameOf(mp.row) }}</span>
                </span>
              }
            }
            @for (b of d.blocks; track $index) {
              <span
                class="chip"
                role="button"
                tabindex="0"
                [class.neutral]="!color(b.row)"
                [class.movable]="movable(b)"
                [class.dragsource]="movePreview()?.row === b.row"
                [style.background]="color(b.row)"
                [style.top.px]="blockTop(b.startMin)"
                [style.height.px]="blockHeight(b.startMin, b.endMin)"
                [style.left]="'calc(' + b.lane + '/' + d.lanes + '*100% + 1px)'"
                [style.width]="'calc(' + 1 + '/' + d.lanes + '*100% - 3px)'"
                (dblclick)="openRow.emit(b.row)"
                (keydown.enter)="openRow.emit(b.row)"
                (contextmenu)="onChipMenu($event, b.row)"
                (pointerdown)="armMove($event, b, d.day)"
              >
                <span class="t">{{ b.contLeft ? '‹ ' : '' }}{{ timeOf(b.row) }}</span>
                <span class="n">{{ nameOf(b.row) }}{{ b.contRight ? ' ›' : '' }}</span>
              </span>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    CHIP_BASE_CSS,
    `
      :host {
        display: block;
      }
      .wg {
        border: 1px solid rgba(0, 0, 0, 0.12);
        background: #fff;
        overflow: auto;
        max-height: calc(100vh - 200px);
      }
      .hdr,
      .body {
        display: grid;
      }
      .hdr {
        position: sticky;
        top: 0;
        z-index: 3;
        background: #f5f5f7;
        border-bottom: 1px solid rgba(0, 0, 0, 0.12);
      }
      .dayhdr {
        padding: 0.3rem 0.5rem;
        border-right: 1px solid rgba(0, 0, 0, 0.12);
        font-size: 0.72rem;
        font-weight: 600;
        color: rgba(0, 0, 0, 0.45);
        text-transform: uppercase;
        display: flex;
        align-items: baseline;
        gap: 0.35rem;
      }
      .dayhdr .dnum {
        font-size: 1rem;
        color: #444;
      }
      .dayhdr.today .dnum {
        background: var(--mat-sys-primary, #3f51b5);
        color: #fff;
        border-radius: 50%;
        min-width: 1.4rem;
        height: 1.4rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }
      .gutter {
        width: 48px;
      }
      .gutter.times {
        position: relative;
      }
      .hlabel {
        position: absolute;
        right: 4px;
        transform: translateY(-50%);
        font-size: 0.66rem;
        color: rgba(0, 0, 0, 0.4);
        font-variant-numeric: tabular-nums;
      }
      .daycol {
        position: relative;
        border-right: 1px solid rgba(0, 0, 0, 0.12);
        background: #fff;
      }
      .daycol.today {
        background: #fafbff;
      }
      .hline {
        position: absolute;
        left: 0;
        right: 0;
        border-top: 1px solid rgba(0, 0, 0, 0.07);
      }
      .subline {
        position: absolute;
        left: 0;
        right: 0;
        border-top: 1px dotted rgba(0, 0, 0, 0.045);
      }
      .gutter .raster {
        margin: 2px 2px 0 2px;
        width: 44px;
        font-size: 0.66rem;
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 4px;
        background: #fff;
        color: rgba(0, 0, 0, 0.6);
      }
      .daycol {
        touch-action: none;
      }
      .createsel {
        position: absolute;
        left: 1px;
        right: 1px;
        background: rgba(63, 81, 181, 0.18);
        border: 1px solid var(--mat-sys-primary, #3f51b5);
        border-radius: 4px;
        z-index: 2;
        font-size: 0.68rem;
        color: var(--mat-sys-primary, #3f51b5);
        padding: 1px 4px;
        pointer-events: none;
        font-variant-numeric: tabular-nums;
      }
      .chip.movable {
        cursor: grab;
        touch-action: none;
      }
      .chip.ghost {
        left: 1px;
        right: 1px;
        border: 1px dashed rgba(0, 0, 0, 0.45);
        opacity: 0.9;
        pointer-events: none;
        z-index: 3;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);
      }
      .chip.dragsource {
        opacity: 0.35;
      }
      .now {
        position: absolute;
        left: 0;
        right: 0;
        border-top: 2px solid #ea4335;
        z-index: 2;
      }
      .chip {
        position: absolute;
        padding: 1px 4px;
        line-height: 1.25;
        border: 1px solid rgba(0, 0, 0, 0.12);
        display: flex;
        flex-direction: column;
        z-index: 1;
      }
      .chip .t {
        white-space: nowrap;
      }
      .chip .n {
        overflow: hidden;
        text-overflow: ellipsis;
      }
    `,
  ],
})
export class WeekGridComponent {
  readonly rows = input.required<Row[]>();
  readonly anchor = input.required<string>();
  /** Scope resources (PRD 100 D3) — >1 enables Swing fixed-slots lanes. */
  readonly scopeResources = input<NamedRef[]>([]);
  /** Double-click / Enter on a chip — the view host runs the shared edit path. */
  readonly openRow = output<Row>();
  /** Right-click on a chip — the view host opens the SHARED row menu (PRD 094). */
  readonly openMenu = output<{ row: Row; x: number; y: number }>();
  /** Drag-move drop (gated by {@link isMovableRow}): day + minute shift. */
  readonly moveBlock = output<{ row: Row; dayDelta: number; minuteDelta: number }>();
  /** Drag-create: a snapped, possibly multi-day time-range selection was released
   *  (Swing SelectionHandler FLOW semantics — ONE continuous datetime interval). */
  readonly createTimeRange = output<{
    fromDay: string;
    startMin: number;
    toDay: string;
    endMin: number;
  }>();

  readonly today = localToday();

  /** Slot raster (Swing "rows per hour" calendar option): 1 = 60m, 2 = 30m, 4 = 15m. */
  readonly rowsPerHour = signal(2);

  onRaster(ev: Event): void {
    this.rowsPerHour.set(Number((ev.target as HTMLSelectElement).value) || 2);
  }

  /** px offsets of the sub-hour gridlines within one hour row. */
  readonly subSlots = computed(() => {
    const n = this.rowsPerHour();
    return Array.from({ length: n - 1 }, (_, i) => ((i + 1) * HOUR_PX) / n);
  });

  readonly layout = computed(() => {
    const selected = this.scopeResources();
    return layoutWeek(
      this.rows(),
      this.anchor(),
      (r) => String(r['start'] ?? ''),
      (r) => String(r['end'] ?? ''),
      {
        selected,
        // Swing parity (PRD 100 OQ2, resolved 2026-07-09): fixed lanes whenever
        // ANY resource is scoped — Swing's isCompactColumns is dead config
        // (hardcoded false, no options UI); compact is only the empty-selection
        // fallback (CalendarWeekViewPresenter:188).
        mode: selected.length > 0 ? 'fixed' : 'compact',
        allocsOf: rowAllocRefs,
      },
    );
  });

  /** Time gutter + one column per day, width proportional to its lane count.
   *  The px minimum floors every lane at MIN_LANE_PX (Swing minBlockWidth
   *  parity) — busy weeks overflow the container and scroll horizontally
   *  instead of squeezing chips into slivers. An explicit minmax (not bare Nfr
   *  = minmax(auto, Nfr)) also keeps header and body columns aligned: the
   *  header text's min-content would otherwise drift them apart. */
  readonly gridCols = computed(
    () =>
      `48px ${this.layout()
        .days.map((d: DayLayout<Row>) => `minmax(${d.lanes * MIN_LANE_PX}px, ${d.lanes}fr)`)
        .join(' ')}`,
  );

  readonly hours = computed(() => {
    const { startHour, endHour } = this.layout();
    return Array.from({ length: endHour - startHour + 1 }, (_, i) => startHour + i);
  });

  readonly bodyHeight = computed(
    () => (this.layout().endHour - this.layout().startHour) * HOUR_PX + 16,
  );

  hourTop(hour: number): number {
    return (hour - this.layout().startHour) * HOUR_PX + 8;
  }

  blockTop(startMin: number): number {
    return ((startMin - this.layout().startHour * 60) / 60) * HOUR_PX + 8;
  }

  blockHeight(startMin: number, endMin: number): number {
    return Math.max(18, ((endMin - startMin) / 60) * HOUR_PX - 2);
  }

  nowTop(): number {
    const now = new Date();
    return this.blockTop(now.getHours() * 60 + now.getMinutes());
  }

  dowOf(day: string): string {
    const idx = weekDays(day).indexOf(day);
    return ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'][idx] ?? '';
  }

  dayNum(day: string): number {
    return Number(day.slice(8, 10));
  }

  readonly color = chipColor;
  readonly timeOf = chipTime;
  readonly nameOf = chipName;

  onChipMenu(ev: MouseEvent, row: Row): void {
    ev.preventDefault();
    this.openMenu.emit({ row, x: ev.clientX, y: ev.clientY });
  }

  /** Drag affordance: gate + not a clipped multi-day segment (v1). */
  movable(b: DayBlockView): boolean {
    return !b.contLeft && !b.contRight && isMovableRow(b.row);
  }

  // --- drag-move (Swing DraggingHandler analog): idle → armed (pointerdown on a
  // movable chip) → dragging (threshold) → drop emits the day+minute shift /
  // ESC cancels. The preview box shows the snapped target interval.
  readonly movePreview = signal<{
    day: string;
    startMin: number;
    endMin: number;
    row: Row;
  } | null>(null);
  private mv: {
    row: Row;
    day: string;
    startMin: number;
    endMin: number;
    chip: HTMLElement;
    colTop: number;
    grabOffsetMin: number;
    startX: number;
    startY: number;
  } | null = null;
  private readonly onMvMove = (ev: PointerEvent) => this.mvMove(ev);
  private readonly onMvUp = () => this.mvUp();
  private readonly onMvCancel = () => this.cancelMove();
  private readonly onMvKey = (ev: KeyboardEvent) => {
    if (ev.key === 'Escape') this.cancelMove();
  };

  armMove(ev: PointerEvent, b: DayBlockView, day: string): void {
    if (ev.button !== 0 || this.mv || !this.movable(b)) return;
    const chip = ev.currentTarget as HTMLElement;
    ev.preventDefault();
    ev.stopPropagation();
    chip.setPointerCapture?.(ev.pointerId);
    const colTop = (chip.parentElement as HTMLElement).getBoundingClientRect().top;
    this.mv = {
      row: b.row,
      day,
      startMin: b.startMin,
      endMin: b.endMin,
      chip,
      colTop,
      grabOffsetMin: this.minAt(ev.clientY, colTop) - b.startMin,
      startX: ev.clientX,
      startY: ev.clientY,
    };
    chip.addEventListener('pointermove', this.onMvMove);
    chip.addEventListener('pointerup', this.onMvUp);
    chip.addEventListener('pointercancel', this.onMvCancel);
    document.addEventListener('keydown', this.onMvKey);
  }

  private mvMove(ev: PointerEvent): void {
    if (!this.mv) return;
    if (
      this.movePreview() === null &&
      Math.hypot(ev.clientX - this.mv.startX, ev.clientY - this.mv.startY) <
        WeekGridComponent.DRAG_THRESHOLD
    ) {
      return;
    }
    const hit = document
      .elementsFromPoint(ev.clientX, ev.clientY)
      .find((el) => el instanceof HTMLElement && el.dataset['day']) as HTMLElement | undefined;
    const day = hit?.dataset['day'] ?? this.mv.day;
    const duration = this.mv.endMin - this.mv.startMin;
    const rawStart = this.minAt(ev.clientY, this.mv.colTop) - this.mv.grabOffsetMin;
    const startMin = Math.max(0, Math.min(DAY_MIN_MAX - duration, this.snapDown(rawStart)));
    this.movePreview.set({ day, startMin, endMin: startMin + duration, row: this.mv.row });
  }

  private mvUp(): void {
    if (!this.mv) return;
    const preview = this.movePreview();
    const source = this.mv;
    this.teardownMove();
    if (!preview) return;
    const dayDelta = daysBetween(source.day, preview.day);
    const minuteDelta = preview.startMin - source.startMin;
    if (dayDelta === 0 && minuteDelta === 0) return;
    this.moveBlock.emit({ row: source.row, dayDelta, minuteDelta });
  }

  private cancelMove(): void {
    this.teardownMove();
  }

  private teardownMove(): void {
    if (!this.mv) return;
    this.mv.chip.removeEventListener('pointermove', this.onMvMove);
    this.mv.chip.removeEventListener('pointerup', this.onMvUp);
    this.mv.chip.removeEventListener('pointercancel', this.onMvCancel);
    document.removeEventListener('keydown', this.onMvKey);
    this.mv = null;
    this.movePreview.set(null);
  }

  selLabelOf(startMin: number, endMin: number): string {
    const f = (m: number) =>
      `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    return `${f(startMin)} – ${f(endMin)}`;
  }

  // --- drag-create (idle → armed → dragging → emit/cancel; Swing SelectionHandler
  // FLOW port: the anchor cell is fixed, the pointer end swaps around it — a
  // cross-day drag selects ONE continuous interval, intermediate days fully).
  private static readonly DRAG_THRESHOLD = 4;
  /** Snapped continuous range while DRAGGING, else null — drives .createsel. */
  readonly selection = signal<{
    fromDay: string;
    startMin: number;
    toDay: string;
    endMin: number;
  } | null>(null);
  private cre: {
    day: string;
    col: HTMLElement;
    rectTop: number;
    anchorMin: number;
    startY: number;
    startX: number;
  } | null = null;
  private readonly onCreMove = (ev: PointerEvent) => this.creMove(ev);
  private readonly onCreUp = () => this.creUp();
  private readonly onCreCancel = () => this.cancelCreate();
  private readonly onCreKey = (ev: KeyboardEvent) => {
    if (ev.key === 'Escape') this.cancelCreate();
  };

  armCreate(ev: PointerEvent, day: string): void {
    // free grid space only — chips handle their own clicks
    if (ev.button !== 0 || this.cre || (ev.target as HTMLElement).closest('.chip')) return;
    const col = ev.currentTarget as HTMLElement;
    ev.preventDefault();
    col.setPointerCapture?.(ev.pointerId);
    const rectTop = col.getBoundingClientRect().top;
    this.cre = {
      day,
      col,
      rectTop,
      anchorMin: this.snapDown(this.minAt(ev.clientY, rectTop)),
      startY: ev.clientY,
      startX: ev.clientX,
    };
    col.addEventListener('pointermove', this.onCreMove);
    col.addEventListener('pointerup', this.onCreUp);
    col.addEventListener('pointercancel', this.onCreCancel);
    document.addEventListener('keydown', this.onCreKey);
  }

  private creMove(ev: PointerEvent): void {
    if (!this.cre) return;
    if (
      this.selection() === null &&
      Math.hypot(ev.clientX - this.cre.startX, ev.clientY - this.cre.startY) <
        WeekGridComponent.DRAG_THRESHOLD
    ) {
      return;
    }
    // cross-day hit-test (pointer capture keeps events on the start column)
    const hit = document
      .elementsFromPoint(ev.clientX, ev.clientY)
      .find((el) => el instanceof HTMLElement && el.dataset['day']) as HTMLElement | undefined;
    const curDay = hit?.dataset['day'] ?? this.cre.day;
    const cur = this.minAt(ev.clientY, this.cre.rectTop);
    const slot = 60 / this.rowsPerHour();
    const anchor = { day: this.cre.day, min: this.cre.anchorMin };
    let sel;
    if (curDay === anchor.day) {
      const [a, b] =
        cur >= anchor.min ? [anchor.min, this.snapUp(cur)] : [this.snapDown(cur), anchor.min + slot];
      sel = { fromDay: anchor.day, startMin: a, toDay: anchor.day, endMin: Math.max(b, a + slot) };
    } else if (curDay > anchor.day) {
      // Swing move(): later slot → anchor is the start, pointer row is the end
      sel = { fromDay: anchor.day, startMin: anchor.min, toDay: curDay, endMin: this.snapUp(cur) };
    } else {
      // earlier slot → pointer is the start, the anchor cell stays included
      sel = {
        fromDay: curDay,
        startMin: this.snapDown(cur),
        toDay: anchor.day,
        endMin: anchor.min + slot,
      };
    }
    this.selection.set(sel);
  }

  private creUp(): void {
    if (!this.cre) return;
    const range = this.selection();
    this.teardownCreate();
    if (range) this.createTimeRange.emit(range);
  }

  private cancelCreate(): void {
    this.teardownCreate();
  }

  private teardownCreate(): void {
    if (!this.cre) return;
    this.cre.col.removeEventListener('pointermove', this.onCreMove);
    this.cre.col.removeEventListener('pointerup', this.onCreUp);
    this.cre.col.removeEventListener('pointercancel', this.onCreCancel);
    document.removeEventListener('keydown', this.onCreKey);
    this.cre = null;
    this.selection.set(null);
  }

  /** Minutes-of-day at a viewport y within the column (clamped to the axis). */
  private minAt(clientY: number, rectTop: number): number {
    const { startHour, endHour } = this.layout();
    const min = startHour * 60 + ((clientY - rectTop - 8) / HOUR_PX) * 60;
    return Math.max(startHour * 60, Math.min(endHour * 60, min));
  }

  private snapDown(min: number): number {
    const slot = 60 / this.rowsPerHour();
    return Math.floor(min / slot) * slot;
  }

  private snapUp(min: number): number {
    const slot = 60 / this.rowsPerHour();
    return Math.ceil(min / slot) * slot;
  }

  /** The selection's overlay segment for one day column (Swing FLOW rendering:
   *  first day anchor→day end, middle days full axis, last day start→pointer). */
  selSegment(day: string): { startMin: number; endMin: number; label: string } | null {
    const s = this.selection();
    if (!s || day < s.fromDay || day > s.toDay) return null;
    const { startHour, endHour } = this.layout();
    const startMin = day === s.fromDay ? s.startMin : startHour * 60;
    const endMin = day === s.toDay ? s.endMin : endHour * 60;
    const f = (m: number) =>
      `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    const label =
      day === s.fromDay
        ? s.fromDay === s.toDay
          ? `${f(s.startMin)} – ${f(s.endMin)}`
          : `${f(s.startMin)} →`
        : day === s.toDay
          ? `→ ${f(s.endMin)}`
          : '';
    return { startMin, endMin, label };
  }
}
