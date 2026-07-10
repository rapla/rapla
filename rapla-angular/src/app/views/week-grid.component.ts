import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';

import { CHIP_BASE_CSS, chipColor, chipName, chipTime, isDraggableRow } from './block-style';
import {
  layoutWeek,
  printMode,
  weekDays,
  type DayBlock,
  type DayLayout,
  type NamedRef,
} from './week-lanes';

type Row = Record<string, unknown>;
type DayBlockView = DayBlock<Row>;

const HOUR_PX = 48;
const DAY_MIN_MAX = 24 * 60;
/** Minimum lane width in px (Swing parity: SwingWeekView floors the slot width
 *  at minBlockWidth and lets the calendar scroll horizontally instead of
 *  squeezing lanes into the viewport). */
const MIN_LANE_PX = 80;
/** A slice of empty column kept to the RIGHT of the lanes so there is always free
 *  grid space to start a new time selection (Swing leaves an empty strip too),
 *  even on a fully-packed day. Blocks pack into `100% - SELECT_GUTTER_PX`. */
const SELECT_GUTTER_PX = 16;

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
  for (const [k, value] of Object.entries(row)) {
    if (k === 'matchedBy' || !Array.isArray(value)) continue; // matchedBy is provenance, not a lane source
    for (const item of value) {
      const o = item as Record<string, unknown> | null;
      if (o && typeof o['id'] === 'string' && typeof o['name'] === 'string') {
        refs.push({ id: o['id'], name: o['name'], isLocation: o['isLocation'] === true });
      }
    }
  }
  return refs;
}

/** The server-computed match provenance for the block (`AppointmentBlock.matchedBy`,
 *  PRD 100 Phase 5): the SELECTED allocatable(s) that admitted it. The authoritative
 *  lane key — a room's block carries the selected BUILDING here, which row cells can't
 *  show. Absent (custom view without the field) ⇒ empty ⇒ heuristic fallback. */
function rowMatchedRefs(row: Row): NamedRef[] {
  const value = row['matchedBy'];
  if (!Array.isArray(value)) return [];
  const refs: NamedRef[] = [];
  for (const item of value) {
    const o = item as Record<string, unknown> | null;
    // name is optional — the builtin view selects only { id } (lanes have no visible
    // label); fall back to the id so the group sort key stays stable.
    if (o && typeof o['id'] === 'string') {
      refs.push({ id: o['id'], name: typeof o['name'] === 'string' ? o['name'] : o['id'] });
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
  host: { '[class.print-stacked]': 'printStacked()' },
  template: `
    <div
      class="wg"
      [style.maxHeight.px]="maxHeight()"
      [style.--wg-hpx]="hourPx() + 'px'"
      [style.--wg-cols]="gridCols()"
      [style.--wg-print-cols]="printCols()"
    >
      <div class="hdr">
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
      <div class="body">
        <div class="gutter times" [style.height]="bodyHeight()">
          @for (h of hours(); track h) {
            <span class="hlabel" [style.top]="hourTop(h)">{{ h }}:00</span>
          }
        </div>
        @for (d of layout().days; track d.day) {
          <div
            class="daycol"
            [class.today]="d.day === today"
            [style.height]="bodyHeight()"
            [attr.data-day]="d.day"
            (pointerdown)="armCreate($event, d.day)"
          >
            <!-- print-stacked only: each day carries its own label + hour axis -->
            <div class="pday">{{ dowOf(d.day) }} {{ dayNum(d.day) }}</div>
            @for (h of hours(); track h) {
              <span class="phlabel" [style.top]="hourTop(h)">{{ h }}:00</span>
              <div class="hline" [style.top]="hourTop(h)"></div>
              <!-- no sub-slots after the last hour line — they'd overflow the axis -->
              @if (h < layout().endHour) {
                @for (sub of subSlots(); track sub) {
                  <div class="subline" [style.top]="hourTop(h, sub)"></div>
                }
              }
            }
            @if (d.day === today) {
              <div class="now" [style.top]="nowTop()"></div>
            }
            @if (selSegment(d.day); as seg) {
              <div
                class="createsel"
                [style.top]="blockTop(seg.startMin)"
                [style.height]="blockHeight(seg.startMin, seg.endMin)"
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
                  [style.top]="blockTop(mp.startMin)"
                  [style.height]="blockHeight(mp.startMin, mp.endMin)"
                >
                  <span class="t">{{ selLabelOf(mp.startMin, mp.endMin) }}</span>
                  <span class="n">{{ nameOf(mp.row) }}</span>
                </span>
              }
            }
            @if (resizePreview(); as rp) {
              @if (rp.day === d.day) {
                <!-- live resize outline: same start, new end -->
                <span
                  class="chip ghost"
                  [class.neutral]="!color(rp.row)"
                  [style.background]="color(rp.row)"
                  [style.top]="blockTop(rp.startMin)"
                  [style.height]="blockHeight(rp.startMin, rp.endMin)"
                >
                  <span class="t">{{ selLabelOf(rp.startMin, rp.endMin) }}</span>
                  <span class="n">{{ nameOf(rp.row) }}</span>
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
                [class.dragsource]="movePreview()?.row === b.row || resizePreview()?.row === b.row"
                [style.background]="color(b.row)"
                [style.top]="blockTop(b.startMin)"
                [style.height]="blockHeight(b.startMin, b.endMin)"
                [style.left]="chipLeft(b.lane, d.lanes)"
                [style.width]="chipWidth(d.lanes)"
                (dblclick)="openRow.emit(b.row)"
                (keydown.enter)="openRow.emit(b.row)"
                (contextmenu)="onChipMenu($event, b.row)"
                (pointerdown)="armMove($event, b, d.day)"
              >
                <span class="t">{{ b.contLeft ? '‹ ' : '' }}{{ timeOf(b.row) }}</span>
                <span class="n">{{ nameOf(b.row) }}{{ b.contRight ? ' ›' : '' }}</span>
                @if (movable(b)) {
                  <span class="rz" (pointerdown)="armResize($event, b, d.day)"></span>
                }
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
      }
      .hdr,
      .body {
        display: grid;
        grid-template-columns: var(--wg-cols);
      }
      /* Print-only helpers (per-day label + hour axis for the stacked layout). */
      .pday,
      .phlabel {
        display: none;
      }
      @media print {
        .raster {
          display: none;
        }
        .wg {
          border: none;
          /* Pin the screen-stretched hour height (inline --wg-hpx) back to the
             compact 48px the PRD 077 page-fit math assumes. */
          --wg-hpx: 48px !important;
        }
        /* Grid mode: drop the 80px lane floor — pure fr weights squeeze the week
           onto the (landscape) page width. printMode() guarantees ≥ 56px lanes,
           else the stacked layout below takes over. */
        .hdr,
        .body {
          grid-template-columns: var(--wg-print-cols);
        }
        /* Stacked mode: one full-width day under the other — horizontal overflow
           becomes vertical flow, which the browser CAN paginate. */
        :host(.print-stacked) .hdr {
          display: none;
        }
        :host(.print-stacked) .body {
          display: block;
        }
        :host(.print-stacked) .gutter.times {
          display: none;
        }
        :host(.print-stacked) .daycol {
          margin: 2.2rem 0 0 52px;
          break-inside: avoid;
        }
        :host(.print-stacked) .pday {
          display: block;
          position: absolute;
          top: -1.5rem;
          left: 0;
          font-size: 0.85rem;
          font-weight: 600;
        }
        :host(.print-stacked) .phlabel {
          display: block;
          position: absolute;
          left: -48px;
          width: 42px;
          text-align: right;
          transform: translateY(-50%);
          font-size: 0.65rem;
          color: rgba(0, 0, 0, 0.55);
        }
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
        min-height: 18px;
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
      .chip .rz {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 7px;
        cursor: ns-resize;
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
        min-height: 18px;
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
  /** Columns to render: 7 = week (Mo–So of the anchor), 1 = day (the anchor day only).
   *  Same grid, lanes, drag + matchedBy machinery either way. */
  readonly dayCount = input(7);
  /** Scope resources (PRD 100 D3) — >1 enables Swing fixed-slots lanes. */
  readonly scopeResources = input<NamedRef[]>([]);
  /** Double-click / Enter on a chip — the view host runs the shared edit path. */
  readonly openRow = output<Row>();
  /** Right-click on a chip — the view host opens the SHARED row menu (PRD 094). */
  readonly openMenu = output<{ row: Row; x: number; y: number }>();
  /** Drag-move drop (gated by {@link isDraggableRow}): day + minute shift. */
  readonly moveBlock = output<{ row: Row; dayDelta: number; minuteDelta: number }>();
  /** Edge-resize drop (PRD 101 Phase 5): the block's new end, minutes-of-day
   *  (start unchanged; the block stays on its day). */
  readonly resizeBlock = output<{ row: Row; endMin: number }>();
  /** Drag-create: a snapped, possibly multi-day time-range selection was released
   *  (Swing SelectionHandler FLOW semantics — ONE continuous datetime interval). */
  readonly createTimeRange = output<{
    fromDay: string;
    startMin: number;
    toDay: string;
    endMin: number;
  }>();

  readonly today = localToday();

  /** The grid is THE vertical scroller — sized to fill the viewport remainder
   *  so the page doesn't grow a second scrollbar (measured, resize-aware). */
  readonly maxHeight = signal<number | null>(null);
  /** Measured header-row height — part of the vertical budget hourPx fills. */
  readonly headerPx = signal(0);
  /** Measured .wg chrome (borders + horizontal scrollbar, offsetHeight −
   *  clientHeight) — a wide week's scrollbar would otherwise eat into the
   *  hour axis and leave a needless vertical scroll. */
  readonly chromePx = signal(2);
  private readonly host = inject(ElementRef);

  constructor() {
    const measure = () => {
      // Never reflow mid-drag: changing the grid height moves the target under the pointer
      // and tears listeners; the drag handlers capture element geometry at gesture start.
      if (this.mv || this.cre || this.rz) return;
      const el = this.host.nativeElement as HTMLElement;
      const top = el.getBoundingClientRect().top;
      this.headerPx.set((el.querySelector('.hdr') as HTMLElement | null)?.offsetHeight ?? 0);
      const wg = el.querySelector('.wg') as HTMLElement | null;
      this.chromePx.set(wg ? wg.offsetHeight - wg.clientHeight : 0);
      const next = Math.max(320, window.innerHeight - top - 12);
      // Only set on a real change — measuring changes maxHeight → grid height → body size →
      // ResizeObserver fires again; the equality guard makes that loop converge in one pass.
      if (this.maxHeight() === null || Math.abs(this.maxHeight()! - next) > 1) {
        this.maxHeight.set(next);
      }
    };
    afterNextRender(measure);
    window.addEventListener('resize', measure);
    // Re-measure when anything above the grid reflows (chips wrap, control strip grows,
    // data loads) — a stale `top` would size the grid too tall and grow a page scrollbar.
    const ro = new ResizeObserver(() => measure());
    ro.observe(document.body);
    // PRD 077 print — the week/day view ALWAYS prints landscape: grid mode needs
    // the width for 7 day columns, and a stacked day gets ~40% wider lanes while
    // a typical 8–18h day block still fits one landscape page. Explicit rule so
    // Chrome's remembered manual choice never leaks in. Document-level because
    // @page can't live in component styles; removed with the component, so
    // table/month keep the user's free choice.
    const pageStyle = document.createElement('style');
    pageStyle.textContent = '@page { size: A4 landscape; }';
    document.head.appendChild(pageStyle);
    inject(DestroyRef).onDestroy(() => {
      pageStyle.remove();
      ro.disconnect();
      window.removeEventListener('resize', measure);
    });
  }

  /** Slot raster (Swing "rows per hour" calendar option): 1 = 60m, 2 = 30m, 4 = 15m. */
  readonly rowsPerHour = signal(2);

  onRaster(ev: Event): void {
    this.rowsPerHour.set(Number((ev.target as HTMLSelectElement).value) || 2);
  }

  /** Hour-fractions of the sub-hour gridlines within one hour row. */
  readonly subSlots = computed(() => {
    const n = this.rowsPerHour();
    return Array.from({ length: n - 1 }, (_, i) => (i + 1) / n);
  });

  /** Hour row height (--wg-hpx): stretches so the time axis fills the measured
   *  viewport height on big screens, floored at HOUR_PX so small screens scroll
   *  instead (the vertical analog of MIN_LANE_PX). Screen-only — @media print
   *  pins the variable back to 48px so PRD 077 page fitting is unaffected.
   *  16 = the body padding baked into bodyHeight. */
  readonly hourPx = computed(() => {
    const mh = this.maxHeight();
    const hours = this.layout().endHour - this.layout().startHour;
    if (mh === null || hours <= 0) return HOUR_PX;
    return Math.max(HOUR_PX, Math.floor((mh - this.headerPx() - this.chromePx() - 16) / hours));
  });

  readonly layout = computed(() => {
    const selected = this.scopeResources();
    const rows = this.rows();
    // A container chip (building/category) never appears as a resource chip, but its
    // blocks carry matchedBy — so scope is "in effect" whenever provenance exists too.
    const scoped = selected.length > 0 || rows.some((r) => rowMatchedRefs(r).length > 0);
    const anchor = this.anchor();
    const days = this.dayCount() === 1 ? [anchor.slice(0, 10)] : weekDays(anchor);
    return layoutWeek(
      rows,
      anchor,
      (r) => String(r['start'] ?? ''),
      (r) => String(r['end'] ?? ''),
      {
        selected,
        // Swing parity (PRD 100 OQ2, resolved 2026-07-09): fixed lanes whenever
        // ANY resource is scoped — Swing's isCompactColumns is dead config
        // (hardcoded false, no options UI); compact is only the empty-selection
        // fallback (CalendarWeekViewPresenter:188).
        mode: scoped ? 'fixed' : 'compact',
        allocsOf: rowAllocRefs,
        matchedByOf: rowMatchedRefs,
      },
      days,
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
        .days.map(
          (d: DayLayout<Row>) =>
            `minmax(${d.lanes * MIN_LANE_PX + SELECT_GUTTER_PX}px, ${d.lanes}fr)`,
        )
        .join(' ')}`,
  );

  /** Print column template — pure fr weights (no px floor): the week always fits
   *  the page width; {@link printStacked} guards readability. */
  readonly printCols = computed(
    () =>
      `48px ${this.layout()
        .days.map((d: DayLayout<Row>) => `${d.lanes}fr`)
        .join(' ')}`,
  );

  /** PRD 077 print — stacked days when the landscape grid would squeeze lanes
   *  below the readable minimum (see printMode in week-lanes.ts). */
  readonly printStacked = computed(
    () => printMode(this.layout().days.map((d: DayLayout<Row>) => d.lanes)) === 'stacked',
  );

  readonly hours = computed(() => {
    const { startHour, endHour } = this.layout();
    return Array.from({ length: endHour - startHour + 1 }, (_, i) => startHour + i);
  });

  readonly bodyHeight = computed(
    () => `calc(var(--wg-hpx) * ${this.layout().endHour - this.layout().startHour} + 16px)`,
  );

  /** Chip x-position: lanes pack into `100% - SELECT_GUTTER_PX`, leaving the trailing
   *  gutter as free grid space for starting a time selection. */
  chipLeft(lane: number, lanes: number): string {
    return `calc((100% - ${SELECT_GUTTER_PX}px) * ${lane} / ${lanes} + 1px)`;
  }

  chipWidth(lanes: number): string {
    return `calc((100% - ${SELECT_GUTTER_PX}px) / ${lanes} - 3px)`;
  }

  /* Vertical geometry is expressed in --wg-hpx (not resolved px) so the print
     stylesheet can pin the hour height independently of the screen value. The
     18px chip minimum lives in CSS (min-height) for the same reason. */
  hourTop(hour: number, frac = 0): string {
    return `calc(var(--wg-hpx) * ${hour - this.layout().startHour + frac} + 8px)`;
  }

  blockTop(startMin: number): string {
    return `calc(var(--wg-hpx) * ${(startMin - this.layout().startHour * 60) / 60} + 8px)`;
  }

  blockHeight(startMin: number, endMin: number): string {
    return `calc(var(--wg-hpx) * ${(endMin - startMin) / 60} - 2px)`;
  }

  nowTop(): string {
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

  /** Drag/resize affordance: gate + not a clipped multi-day segment (a resize
   *  keeps the block on its day; a clipped segment has no true start/end here). */
  movable(b: DayBlockView): boolean {
    return !b.contLeft && !b.contRight && isDraggableRow(b.row);
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

  // --- edge-resize (Swing AppointmentResize analog): grab the bottom handle,
  // drag the end; start stays fixed and the block stays on its day. Drop emits
  // the new end minute-of-day / ESC cancels. The preview shows the live extent.
  readonly resizePreview = signal<{
    day: string;
    startMin: number;
    endMin: number;
    row: Row;
  } | null>(null);
  private rz: {
    row: Row;
    day: string;
    startMin: number;
    endMin: number;
    colTop: number;
    handle: HTMLElement;
  } | null = null;
  private readonly onRzMove = (ev: PointerEvent) => this.rzMove(ev);
  private readonly onRzUp = () => this.rzUp();
  private readonly onRzCancel = () => this.cancelResize();
  private readonly onRzKey = (ev: KeyboardEvent) => {
    if (ev.key === 'Escape') this.cancelResize();
  };

  armResize(ev: PointerEvent, b: DayBlockView, day: string): void {
    if (ev.button !== 0 || this.rz || !this.movable(b)) return;
    const handle = ev.currentTarget as HTMLElement;
    ev.preventDefault();
    ev.stopPropagation(); // don't also arm a move on the parent chip
    handle.setPointerCapture?.(ev.pointerId);
    const colTop = (handle.closest('.daycol') as HTMLElement).getBoundingClientRect().top;
    this.rz = { row: b.row, day, startMin: b.startMin, endMin: b.endMin, colTop, handle };
    handle.addEventListener('pointermove', this.onRzMove);
    handle.addEventListener('pointerup', this.onRzUp);
    handle.addEventListener('pointercancel', this.onRzCancel);
    document.addEventListener('keydown', this.onRzKey);
  }

  private rzMove(ev: PointerEvent): void {
    if (!this.rz) return;
    const slot = 60 / this.rowsPerHour();
    const endMin = Math.max(
      this.rz.startMin + slot,
      this.snapUp(this.minAt(ev.clientY, this.rz.colTop)),
    );
    this.resizePreview.set({
      day: this.rz.day,
      startMin: this.rz.startMin,
      endMin,
      row: this.rz.row,
    });
  }

  private rzUp(): void {
    if (!this.rz) return;
    const preview = this.resizePreview();
    const source = this.rz;
    this.teardownResize();
    if (!preview || preview.endMin === source.endMin) return;
    this.resizeBlock.emit({ row: source.row, endMin: preview.endMin });
  }

  private cancelResize(): void {
    this.teardownResize();
  }

  private teardownResize(): void {
    if (!this.rz) return;
    this.rz.handle.removeEventListener('pointermove', this.onRzMove);
    this.rz.handle.removeEventListener('pointerup', this.onRzUp);
    this.rz.handle.removeEventListener('pointercancel', this.onRzCancel);
    document.removeEventListener('keydown', this.onRzKey);
    this.rz = null;
    this.resizePreview.set(null);
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
        cur >= anchor.min
          ? [anchor.min, this.snapUp(cur)]
          : [this.snapDown(cur), anchor.min + slot];
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
    const min = startHour * 60 + ((clientY - rectTop - 8) / this.hourPx()) * 60;
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
