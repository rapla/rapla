import { Component, computed, input, output, signal } from '@angular/core';

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
                (pointerdown)="armSelect($event, day)"
              >
                <span class="daynum">{{ dayNum(day) }}</span>
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
                [style.background]="color(c.row)"
                [style.left]="'calc(' + c.col + '/7*100% + 2px)'"
                [style.width]="'calc(' + c.span + '/7*100% - 5px)'"
                [style.top.px]="c.top"
                (click)="onChipClick(c.row)"
                (keydown.enter)="onChipClick(c.row)"
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
        border-radius: 4px;
        font-size: 0.72rem;
        line-height: 20px;
        height: 20px;
        padding: 0 5px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        color: #fff;
        cursor: pointer;
        user-select: none;
        box-sizing: border-box;
      }
      .chip.neutral {
        background: #e4e6ee;
        color: #333;
      }
      .chip .t {
        opacity: 0.85;
        font-variant-numeric: tabular-nums;
        margin-right: 0.25em;
      }
    `,
  ],
})
export class MonthGridComponent {
  readonly rows = input.required<Row[]>();
  readonly anchor = input.required<string>();
  readonly openEvent = output<string>();
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

  color(row: Row): string | null {
    const c = row['color'];
    return typeof c === 'string' && c ? c : null;
  }

  timeOf(row: Row): string {
    const t = row['times'];
    return typeof t === 'string' && t ? t : String(row['start'] ?? '').slice(11, 16);
  }

  nameOf(row: Row): string {
    return String(row['name'] ?? '');
  }

  onChipClick(row: Row): void {
    const id = (row['reservation'] as { id?: string } | undefined)?.id;
    if (id) this.openEvent.emit(id);
  }

  // --- drag-create selection (state machine: idle → armed → dragging → emit/cancel).
  // Pointer semantics ported from the Swing SelectionHandler / the PRD 095 prototype:
  // capture on the start cell, hit-test the hovered cell via elementsFromPoint.
  private static readonly DRAG_THRESHOLD = 4;
  /** Sorted [from, to] while DRAGGING, else null — drives the .selecting cells. */
  readonly selection = signal<{ from: string; to: string } | null>(null);
  private sel: { startDay: string; endDay: string; cell: HTMLElement; startX: number; startY: number } | null = null;
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
