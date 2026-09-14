import { Component, computed, input, output, signal } from '@angular/core';

import { CHIP_BASE_CSS, chipColor, chipName, chipTime, isDraggableRow } from './block-style';
import { monthGridDays, chunkWeek, TOP0, type WeekChunk } from './month-chunks';

type Row = Record<string, unknown>;

interface WeekRender {
  days: string[];
  chunks: WeekChunk<Row>[];
  minHeight: number;
}

function shiftDay(iso: string, n: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
}

function localToday(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/**
 * Month calendar grid with spanning bars (PRD 095). Renders the weeks of the
 * anchor month (Monday-first) with generic GraphQL rows as absolutely
 * positioned chips/bars — multi-day blocks span their week as ONE bar and get
 * ‹ › continuation markers at week boundaries. Chunking/stacking math lives in
 * {@code month-chunks.ts}; this component only renders.
 */
@Component({
  selector: 'app-month-grid',
  template: `
    <div class="grid">
      <div class="dowrow">
        @for (d of dows; track d) {
          <div class="dow">{{ d }}</div>
        }
      </div>
      @for (week of weeks(); track week.days[0]) {
        <div class="week">
          <div class="wcells" [style.minHeight.px]="week.minHeight">
            @for (day of week.days; track day) {
              <div
                class="cell"
                [attr.data-day]="day"
                [class.other]="isOther(day)"
                [class.today]="day === today"
                [class.selecting]="isSelecting(day)"
                [class.droptarget]="moveTarget()?.day === day"
                (pointerdown)="armSelect($event, day)"
              >
                <span class="daynum">{{ dayNum(day) }}</span>
                @if (moveTarget(); as mt) {
                  @if (mt.day === day) {
                    <!-- the dragged block itself travels into the target cell -->
                    <span
                      class="chip ghost"
                      [class.neutral]="!color(mt.row)"
                      [style.background]="color(mt.row)"
                      ><span class="t">{{ timeOf(mt.row) }}</span
                      >{{ nameOf(mt.row) }}</span
                    >
                  }
                }
              </div>
            }
          </div>
          <div class="wevents">
            @for (c of week.chunks; track $index) {
              <span
                class="chip"
                role="button"
                tabindex="0"
                [class.neutral]="!color(c.row)"
                [class.movable]="isMovable(c.row)"
                [class.dragsource]="moveTarget()?.row === c.row"
                [style.background]="color(c.row)"
                [style.left]="'calc(' + c.col + '/7*100% + 2px)'"
                [style.width]="'calc(' + c.span + '/7*100% - 5px)'"
                [style.top.px]="c.top"
                (dblclick)="openRow.emit(c.row)"
                (keydown.enter)="openRow.emit(c.row)"
                (contextmenu)="onChipMenu($event, c.row)"
                (pointerdown)="armMove($event, c.row)"
                >{{ c.contLeft ? '‹ ' : '' }}<span class="t">{{ timeOf(c.row) }}</span
                >{{ nameOf(c.row) }}{{ c.contRight ? ' ›' : '' }}</span
              >
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [
    CHIP_BASE_CSS,
    `
      :host {
        display: block;
      }
      .grid {
        border: 1px solid rgba(0, 0, 0, 0.12);
        background: #fff;
      }
      .dowrow {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        background: #f5f5f7;
      }
      .dow {
        padding: 0.3rem 0.5rem;
        font-size: 0.72rem;
        font-weight: 600;
        color: rgba(0, 0, 0, 0.45);
        text-transform: uppercase;
        border-right: 1px solid rgba(0, 0, 0, 0.12);
      }
      .week {
        position: relative;
      }
      .wcells {
        display: grid;
        grid-template-columns: repeat(7, 1fr);
        min-height: 100px;
      }
      .cell {
        background: #fff;
        padding: 0.2rem 0.25rem;
        border-right: 1px solid rgba(0, 0, 0, 0.12);
        border-top: 1px solid rgba(0, 0, 0, 0.12);
      }
      .cell {
        touch-action: none;
      }
      .cell.selecting {
        background: #e3e7f8;
        outline: 1px solid var(--mat-sys-primary, #3f51b5);
        outline-offset: -1px;
      }
      .cell.droptarget {
        background: #e3e7f8;
        outline: 1px dashed var(--mat-sys-primary, #3f51b5);
        outline-offset: -1px;
      }
      .chip.movable {
        cursor: grab;
        touch-action: none;
      }
      .cell .chip.ghost {
        display: block;
        margin-top: 2px;
        border: 1px dashed rgba(0, 0, 0, 0.45);
        opacity: 0.9;
        pointer-events: none;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.25);
      }
      .chip.dragsource {
        opacity: 0.35;
      }
      .cell.other {
        background: #f7f7f7;
      }
      .cell.other .daynum {
        color: #bbb;
      }
      .cell.today .daynum {
        background: var(--mat-sys-primary, #3f51b5);
        color: #fff;
        border-radius: 50%;
      }
      .daynum {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1.5rem;
        height: 1.5rem;
        font-size: 0.75rem;
        color: #555;
      }
      .wevents {
        position: absolute;
        inset: 0;
        pointer-events: none;
      }
      .wevents .chip {
        position: absolute;
        pointer-events: auto;
      }
      .chip {
        line-height: 20px;
        height: 20px;
        padding: 0 5px;
        white-space: nowrap;
      }
      .chip .t {
        margin-right: 0.25em;
      }
    `,
  ],
})
export class MonthGridComponent {
  readonly rows = input.required<Row[]>();
  readonly anchor = input.required<string>();
  /** Double-click / Enter on a chip — the view host runs the shared edit path. */
  readonly openRow = output<Row>();
  /** Right-click on a chip — the view host opens the SHARED row menu (PRD 094). */
  readonly openMenu = output<{ row: Row; x: number; y: number }>();
  /** Drag-move drop (gated by {@link isDraggableRow}): whole-day shift. The
   *  scope (EVENT/SERIE/SINGLE) is resolved by the shared view-host dispatch —
   *  month is move-only (no resize; Swing parity), a whole-day shift keeps the
   *  time-of-day (≙ keepTime). */
  readonly moveBlock = output<{ row: Row; dayDelta: number; minuteDelta: number }>();
  /** PRD 095 Phase 3 — drag over free cell space selects a day range; released
   *  selection emits {from, to} ('YYYY-MM-DD', to inclusive, sorted). */
  readonly createRange = output<{ from: string; to: string }>();

  readonly dows = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
  readonly today = localToday();

  readonly weeks = computed<WeekRender[]>(() => {
    const rows = this.rows();
    return monthGridDays(this.anchor()).map((days) => {
      const chunks = chunkWeek(
        rows,
        days,
        (r) => this.startDay(r),
        (r) => this.endDay(r),
      );
      const maxBottom = chunks.reduce((m, c) => Math.max(m, c.bottom), TOP0);
      return { days, chunks, minHeight: Math.max(100, maxBottom + 8) };
    });
  });

  private readonly anchorMonth = computed(() => this.anchor().slice(0, 7));

  isOther(day: string): boolean {
    return day.slice(0, 7) !== this.anchorMonth();
  }

  dayNum(day: string): number {
    return Number(day.slice(8, 10));
  }

  private startDay(row: Row): string {
    return String(row['start'] ?? '').slice(0, 10);
  }

  /** Inclusive end day: a block ending at T00:00:00 does NOT cover that day. */
  private endDay(row: Row): string {
    const start = this.startDay(row);
    const end = String(row['end'] ?? '');
    let day = end.slice(0, 10);
    if (day && end.slice(11) === '00:00:00') day = shiftDay(day, -1);
    return !day || day < start ? start : day;
  }

  readonly color = chipColor;
  readonly timeOf = chipTime;
  readonly nameOf = chipName;

  onChipMenu(ev: MouseEvent, row: Row): void {
    ev.preventDefault();
    this.openMenu.emit({ row, x: ev.clientX, y: ev.clientY });
  }

  readonly isMovable = isDraggableRow;

  // --- drag-move (day-granular): idle → armed (pointerdown on a movable chip) →
  // dragging (threshold) → drop emits the whole-day shift / ESC cancels. The
  // hovered target cell highlights via {@link moveTarget}.
  readonly moveTarget = signal<{ day: string; row: Row } | null>(null);
  private mv: {
    row: Row;
    sourceDay: string;
    chip: HTMLElement;
    startX: number;
    startY: number;
  } | null = null;
  private readonly onMvMove = (ev: PointerEvent) => this.mvMove(ev);
  private readonly onMvUp = () => this.mvUp();
  private readonly onMvCancel = () => this.cancelMove();
  private readonly onMvKey = (ev: KeyboardEvent) => {
    if (ev.key === 'Escape') this.cancelMove();
  };

  armMove(ev: PointerEvent, row: Row): void {
    if (ev.button !== 0 || this.mv || !isDraggableRow(row)) return;
    const sourceDay = this.dayAt(ev.clientX, ev.clientY);
    if (!sourceDay) return;
    const chip = ev.currentTarget as HTMLElement;
    ev.preventDefault();
    ev.stopPropagation();
    chip.setPointerCapture?.(ev.pointerId);
    this.mv = { row, sourceDay, chip, startX: ev.clientX, startY: ev.clientY };
    chip.addEventListener('pointermove', this.onMvMove);
    chip.addEventListener('pointerup', this.onMvUp);
    chip.addEventListener('pointercancel', this.onMvCancel);
    document.addEventListener('keydown', this.onMvKey);
  }

  private mvMove(ev: PointerEvent): void {
    if (!this.mv) return;
    if (
      this.moveTarget() === null &&
      Math.hypot(ev.clientX - this.mv.startX, ev.clientY - this.mv.startY) <
        MonthGridComponent.DRAG_THRESHOLD
    ) {
      return;
    }
    this.moveTarget.set({
      day: this.dayAt(ev.clientX, ev.clientY) ?? this.mv.sourceDay,
      row: this.mv.row,
    });
  }

  private mvUp(): void {
    if (!this.mv) return;
    const target = this.moveTarget();
    const source = this.mv;
    this.teardownMove();
    if (!target || target.day === source.sourceDay) return;
    const dayDelta = Math.round(
      (Date.parse(`${target.day}T00:00:00Z`) - Date.parse(`${source.sourceDay}T00:00:00Z`)) /
        86400000,
    );
    this.moveBlock.emit({ row: source.row, dayDelta, minuteDelta: 0 });
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
    this.moveTarget.set(null);
  }

  private dayAt(x: number, y: number): string | null {
    const hit = document
      .elementsFromPoint(x, y)
      .find((el) => el instanceof HTMLElement && el.dataset['day']) as HTMLElement | undefined;
    return hit?.dataset['day'] ?? null;
  }

  // --- drag-create selection (state machine: idle → armed → dragging → emit/cancel).
  // Pointer semantics ported from the Swing SelectionHandler / the PRD 095 prototype:
  // capture on the start cell, hit-test the hovered cell via elementsFromPoint.
  private static readonly DRAG_THRESHOLD = 4;
  /** Sorted [from, to] while DRAGGING, else null — drives the .selecting cells. */
  readonly selection = signal<{ from: string; to: string } | null>(null);
  private sel: {
    startDay: string;
    endDay: string;
    cell: HTMLElement;
    startX: number;
    startY: number;
  } | null = null;
  private readonly onSelMove = (ev: PointerEvent) => this.selMove(ev);
  private readonly onSelUp = () => this.selUp();
  private readonly onSelCancel = () => this.cancelSelection();
  private readonly onSelKey = (ev: KeyboardEvent) => {
    if (ev.key === 'Escape') this.cancelSelection();
  };

  isSelecting(day: string): boolean {
    const s = this.selection();
    return s !== null && day >= s.from && day <= s.to;
  }

  armSelect(ev: PointerEvent, day: string): void {
    if (ev.button !== 0 || this.sel) return;
    const cell = ev.currentTarget as HTMLElement;
    ev.preventDefault();
    cell.setPointerCapture?.(ev.pointerId);
    this.sel = { startDay: day, endDay: day, cell, startX: ev.clientX, startY: ev.clientY };
    cell.addEventListener('pointermove', this.onSelMove);
    cell.addEventListener('pointerup', this.onSelUp);
    cell.addEventListener('pointercancel', this.onSelCancel);
    document.addEventListener('keydown', this.onSelKey);
  }

  private selMove(ev: PointerEvent): void {
    if (!this.sel) return;
    if (
      this.selection() === null &&
      Math.hypot(ev.clientX - this.sel.startX, ev.clientY - this.sel.startY) <
        MonthGridComponent.DRAG_THRESHOLD
    ) {
      return;
    }
    const hit = document
      .elementsFromPoint(ev.clientX, ev.clientY)
      .find((el) => el instanceof HTMLElement && el.dataset['day']) as HTMLElement | undefined;
    if (hit?.dataset['day']) this.sel.endDay = hit.dataset['day'];
    const [from, to] = [this.sel.startDay, this.sel.endDay].sort();
    this.selection.set({ from, to });
  }

  private selUp(): void {
    if (!this.sel) return;
    const range = this.selection();
    this.teardownSelection();
    if (range) this.createRange.emit(range);
  }

  private cancelSelection(): void {
    this.teardownSelection();
  }

  private teardownSelection(): void {
    if (!this.sel) return;
    this.sel.cell.removeEventListener('pointermove', this.onSelMove);
    this.sel.cell.removeEventListener('pointerup', this.onSelUp);
    this.sel.cell.removeEventListener('pointercancel', this.onSelCancel);
    document.removeEventListener('keydown', this.onSelKey);
    this.sel = null;
    this.selection.set(null);
  }
}
