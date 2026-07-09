import { Component, computed, inject } from '@angular/core';
import { MatDatepickerModule, type MatDatepickerInputEvent } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { provideNativeDateAdapter } from '@angular/material/core';

import { ViewStateStore } from '../state/view-state-store';

/** Local-midnight {@code Date} for a 'YYYY-MM-DD' string (calendar day, no TZ drift for display). */
function toDate(dateOnly: string): Date | null {
  if (!dateOnly) return null;
  const [y, m, d] = dateOnly.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** 'YYYY-MM-DD' from a {@code Date}'s local calendar components. */
function toDateOnly(date: Date): string {
  const yy = date.getFullYear().toString().padStart(4, '0');
  const mm = (date.getMonth() + 1).toString().padStart(2, '0');
  const dd = date.getDate().toString().padStart(2, '0');
  return `${yy}-${mm}-${dd}`;
}

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

/** The window of the month containing {@code dateIso}: [1st .. 1st-of-next-month) at T00:00:00. */
export function monthWindowOf(dateIso: string): { from: string; to: string } {
  const [y, m] = datePart(dateIso).split('-').map(Number);
  return { from: firstOfMonth(y, m), to: firstOfMonth(m === 12 ? y + 1 : y, m === 12 ? 1 : m + 1) };
}

/** Snap to the month of {@code window.from}, then shift by {@code delta} months (PRD 095 D4). */
export function shiftMonth(
  window: { from: string; to: string },
  delta: number,
): {
  from: string;
  to: string;
} {
  const [y, m] = datePart(window.from).split('-').map(Number);
  const index = y * 12 + (m - 1) + delta;
  const yy = Math.floor(index / 12);
  const mm = index - yy * 12 + 1;
  return monthWindowOf(firstOfMonth(yy, mm));
}

/** German month label ('Juli 2026') for a LocalDateTime string's month. */
export function monthLabel(fromIso: string): string {
  const [y, m] = datePart(fromIso).split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString('de-DE', { month: 'long', year: 'numeric' });
}

function firstOfMonth(year: number, month: number): string {
  return `${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-01T00:00:00`;
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
  imports: [MatDatepickerModule, MatFormFieldModule, MatInputModule],
  providers: [provideNativeDateAdapter()],
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
      @if (isMonth()) {
        <!-- MONTH: navigation steps whole months; the window is the anchor month (PRD 095 D4). -->
        <div class="nav">
          <button class="navbtn" title="zurück" (click)="prev()">◀</button>
          <span class="range month-label">{{ monthLabelText() }}</span>
          <button class="navbtn" title="vor" (click)="next()">▶</button>
          <button class="navbtn today" (click)="today()">Heute</button>
        </div>
      } @else if (isWeek()) {
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
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Von</mat-label>
            <input matInput [matDatepicker]="fromPicker" [value]="fromDate()" (dateChange)="onFrom($event)" />
            <mat-datepicker-toggle matIconSuffix [for]="fromPicker" />
            <mat-datepicker #fromPicker />
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Bis</mat-label>
            <input matInput [matDatepicker]="toPicker" [value]="toDate()" (dateChange)="onTo($event)" />
            <mat-datepicker-toggle matIconSuffix [for]="toPicker" />
            <mat-datepicker #toPicker />
          </mat-form-field>
        </div>
      }
      <!-- Result summary of the active view ("68 Termine"), published by the view host. -->
      @if (viewState.resultInfo(); as info) {
        <span class="result-info">{{ info }}</span>
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
      .range-edit mat-form-field {
        width: 10rem;
        font-size: 0.78rem;
      }
      .result-info {
        margin-left: auto;
        font-size: 0.78rem;
        color: rgba(0, 0, 0, 0.55);
        white-space: nowrap;
      }
    `,
  ],
})
export class ViewControlStripComponent {
  protected readonly viewState = inject(ViewStateStore);

  private static readonly LABELS: Record<string, string> = {
    table: 'Tabelle',
    grouped: 'Gruppiert',
    week: 'Woche',
    month: 'Monat',
    day: 'Tag',
    program: 'Programm',
  };

  modeLabel(mode: string): string {
    return ViewControlStripComponent.LABELS[mode] ?? mode;
  }

  /** WEEK-RANGE layout (navigation + read-only range) when the active mode is the
   *  time-grid 'week' or the 'grouped' section list (a week's appointments grouped by
   *  the group column, navigated by week) and the server offers it; otherwise TABLE
   *  layout (editable from/to, no nav). */
  protected readonly isWeek = computed(() => {
    const m = this.viewState.renderMode();
    return (m === 'week' || m === 'grouped' || m === 'day') && this.viewState.renderModes().includes(m);
  });

  /** DAY grid — navigates by a single day (week/grouped step by a week). */
  protected readonly isDay = computed(
    () => this.viewState.renderMode() === 'day' && this.viewState.renderModes().includes('day'),
  );

  /** MONTH layout (◀ Monat Jahr ▶ Heute) when the active mode is 'month' and the server offers it. */
  protected readonly isMonth = computed(
    () => this.viewState.renderModes().includes('month') && this.viewState.renderMode() === 'month',
  );

  protected readonly monthLabelText = computed<string>(() => {
    const w = this.viewState.window();
    return w ? monthLabel(w.from) : '—';
  });

  protected readonly rangeLabel = computed<string>(() => {
    const w = this.viewState.window();
    if (!w) {
      return '—';
    }
    return `${datePart(w.from)} … ${datePart(w.to)}`;
  });

  /** {@code Date} value for the Material datepickers (TABLE mode). */
  protected readonly fromDate = computed<Date | null>(() => {
    const w = this.viewState.window();
    return w ? toDate(datePart(w.from)) : null;
  });
  protected readonly toDate = computed<Date | null>(() => {
    const w = this.viewState.window();
    return w ? toDate(datePart(w.to)) : null;
  });

  onFrom(event: MatDatepickerInputEvent<Date>): void {
    const w = this.viewState.window();
    if (w && event.value) this.viewState.setWindow({ from: `${toDateOnly(event.value)}T00:00:00`, to: w.to });
  }

  onTo(event: MatDatepickerInputEvent<Date>): void {
    const w = this.viewState.window();
    if (w && event.value) this.viewState.setWindow({ from: w.from, to: `${toDateOnly(event.value)}T00:00:00` });
  }

  prev(): void {
    if (this.isMonth()) this.shiftMonths(-1);
    else this.shift(this.isDay() ? -1 : -7);
  }

  next(): void {
    if (this.isMonth()) this.shiftMonths(1);
    else this.shift(this.isDay() ? 1 : 7);
  }

  today(): void {
    if (this.isMonth()) {
      const now = new Date();
      this.viewState.setWindow(monthWindowOf(`${toDateOnly(now)}T00:00:00`));
      return;
    }
    const w = this.viewState.window();
    if (!w) return;
    this.viewState.setWindow(todayWindow(w, new Date()));
  }

  private shiftMonths(delta: number): void {
    const w = this.viewState.window();
    if (!w) return;
    this.viewState.setWindow(shiftMonth(w, delta));
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
