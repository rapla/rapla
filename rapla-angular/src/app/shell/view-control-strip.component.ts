import { Component, computed, inject } from '@angular/core';

import { ViewStateStore, type RenderMode } from '../state/view-state-store';

/** Shift a 'YYYY-MM-DDTHH:mm:ss' LocalDateTime string by {@code days} (UTC-stable, no timezone drift). */
export function shiftLocalDateTime(value: string, days: number): string {
  const [datePart, timePart = '00:00:00'] = value.split('T');
  const [y, m, d] = datePart.split('-').map(Number);
  const base = Date.UTC(y, m - 1, d);
  const shifted = new Date(base + days * 86_400_000);
  const yy = shifted.getUTCFullYear().toString().padStart(4, '0');
  const mm = (shifted.getUTCMonth() + 1).toString().padStart(2, '0');
  const dd = shifted.getUTCDate().toString().padStart(2, '0');
  return `${yy}-${mm}-${dd}T${timePart}`;
}

/** The date-only prefix ('YYYY-MM-DD') of a 'YYYY-MM-DDTHH:mm:ss' LocalDateTime string. */
export function datePart(value: string): string {
  return value.split('T')[0];
}

/** Monday 00:00 of {@code now}'s week as a zoneless LocalDateTime (UTC-stable). */
export function weekStartLocalDateTime(now: Date): string {
  const day = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const mondayOffset = (day.getUTCDay() + 6) % 7; // Mon=0 … Sun=6
  day.setUTCDate(day.getUTCDate() - mondayOffset);
  return `${day.toISOString().slice(0, 10)}T00:00:00`;
}

/** Whole days between two LocalDateTime strings (date parts only). */
export function daysBetween(fromIso: string, toIso: string): number {
  const [fy, fm, fd] = datePart(fromIso).split('-').map(Number);
  const [ty, tm, td] = datePart(toIso).split('-').map(Number);
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86_400_000);
}

/** Re-anchor a window to this week's Monday, preserving its span (the "Heute" jump). */
export function todayWindow(current: { from: string; to: string }, now: Date): {
  from: string;
  to: string;
} {
  const from = weekStartLocalDateTime(now);
  return { from, to: shiftLocalDateTime(from, daysBetween(current.from, current.to)) };
}

/**
 * PRD 077 — the view-control strip: a segmented render-mode switcher (Woche/
 * Tabelle/Monat/Tag/Programm) plus a date-nav row (◀ Heute ▶) that walks the
 * {@link ViewStateStore} window in 7-day steps. Read/writes are purely on the
 * store; the GraphQL refetch reacts to the resulting signals elsewhere.
 */
@Component({
  selector: 'app-view-control-strip',
  imports: [],
  template: `
    <div class="strip">
      <!-- Mode switch — only shown when the server advertises more than one render mode. -->
      @if (viewState.renderModes().length > 1) {
        <div class="modes">
          @for (m of viewState.renderModes(); track m) {
            <button [class.on]="viewState.renderMode() === m" (click)="viewState.setRenderMode(m)">
              {{ modeLabel(m) }}
            </button>
          }
        </div>
      }
      @if (isWeek()) {
        <!-- WEEK: navigation walks the window; range is read-only. -->
        <div class="nav">
          <button class="navbtn" title="zurück" (click)="prev()">◀</button>
          <button class="navbtn today" (click)="today()">Heute</button>
          <button class="navbtn" title="vor" (click)="next()">▶</button>
          <span class="range">{{ rangeLabel() }}</span>
        </div>
      } @else {
        <!-- TABLE: pick an arbitrary from/to range; no navigation. -->
        <div class="range-edit">
          <label>Von <input type="date" [value]="fromDate()" (change)="onFrom($event)" /></label>
          <label>Bis <input type="date" [value]="toDate()" (change)="onTo($event)" /></label>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .strip {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 0.4rem 0.75rem;
        flex-wrap: wrap;
      }
      .modes {
        display: flex;
        gap: 0.3rem;
      }
      .modes button {
        border: 1px solid rgba(0, 0, 0, 0.15);
        background: #fff;
        border-radius: 6px;
        padding: 0.35rem 0.7rem;
        font-size: 0.78rem;
        cursor: pointer;
        color: rgba(0, 0, 0, 0.6);
      }
      .modes button.on {
        background: var(--mat-sys-primary, #3f51b5);
        color: #fff;
        border-color: transparent;
        font-weight: 600;
      }
      .nav {
        display: flex;
        align-items: center;
        gap: 0.4rem;
      }
      .nav .navbtn {
        border: 1px solid rgba(0, 0, 0, 0.15);
        background: #fff;
        border-radius: 6px;
        padding: 0.3rem 0.6rem;
        font-size: 0.78rem;
        cursor: pointer;
        color: rgba(0, 0, 0, 0.7);
      }
      .nav .navbtn.today {
        font-weight: 600;
      }
      .nav .range {
        font-size: 0.78rem;
        color: rgba(0, 0, 0, 0.6);
        margin-left: 0.4rem;
      }
      .range-edit {
        display: flex;
        align-items: center;
        gap: 0.75rem;
      }
      .range-edit label {
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
        font-size: 0.72rem;
        color: rgba(0, 0, 0, 0.55);
      }
      .range-edit input[type='date'] {
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 6px;
        padding: 0.25rem 0.4rem;
        font-size: 0.78rem;
      }
    `,
  ],
})
export class ViewControlStripComponent {
  protected readonly viewState = inject(ViewStateStore);

  private static readonly LABELS: Record<string, string> = {
    table: 'Tabelle',
    week: 'Woche',
    month: 'Monat',
    day: 'Tag',
    program: 'Programm',
  };

  modeLabel(mode: string): string {
    return ViewControlStripComponent.LABELS[mode] ?? mode;
  }

  /** WEEK layout (navigation + read-only range) when the active mode is 'week'
   *  and the server offers it; otherwise TABLE layout (editable from/to, no nav). */
  protected readonly isWeek = computed(
    () => this.viewState.renderModes().includes('week') && this.viewState.renderMode() === 'week',
  );

  protected readonly rangeLabel = computed<string>(() => {
    const w = this.viewState.window();
    if (!w) {
      return '—';
    }
    return `${datePart(w.from)} … ${datePart(w.to)}`;
  });

  /** {@code YYYY-MM-DD} value for the native date inputs (TABLE mode). */
  protected readonly fromDate = computed<string>(() => {
    const w = this.viewState.window();
    return w ? datePart(w.from) : '';
  });
  protected readonly toDate = computed<string>(() => {
    const w = this.viewState.window();
    return w ? datePart(w.to) : '';
  });

  onFrom(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    const w = this.viewState.window();
    if (w && value) this.viewState.setWindow({ from: `${value}T00:00:00`, to: w.to });
  }

  onTo(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    const w = this.viewState.window();
    if (w && value) this.viewState.setWindow({ from: w.from, to: `${value}T00:00:00` });
  }

  prev(): void {
    this.shift(-7);
  }

  next(): void {
    this.shift(7);
  }

  today(): void {
    const w = this.viewState.window();
    if (!w) return;
    this.viewState.setWindow(todayWindow(w, new Date()));
  }

  private shift(days: number): void {
    const w = this.viewState.window();
    if (!w) {
      return;
    }
    this.viewState.setWindow({
      from: shiftLocalDateTime(w.from, days),
      to: shiftLocalDateTime(w.to, days),
    });
  }
}
