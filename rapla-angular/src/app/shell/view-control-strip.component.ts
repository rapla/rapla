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
      <div class="modes">
        @for (m of modes; track m.mode) {
          <button [class.on]="viewState.renderMode() === m.mode" (click)="viewState.setRenderMode(m.mode)">
            {{ m.label }}
          </button>
        }
      </div>
      <div class="nav">
        <button class="navbtn" title="zurück" (click)="prev()">◀</button>
        <button class="navbtn today" (click)="today()">Heute</button>
        <button class="navbtn" title="vor" (click)="next()">▶</button>
        <span class="range">{{ rangeLabel() }}</span>
      </div>
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
    `,
  ],
})
export class ViewControlStripComponent {
  protected readonly viewState = inject(ViewStateStore);

  protected readonly modes: { mode: RenderMode; label: string }[] = [
    { mode: 'week', label: 'Woche' },
    { mode: 'table', label: 'Tabelle' },
    { mode: 'month', label: 'Monat' },
    { mode: 'day', label: 'Tag' },
    { mode: 'program', label: 'Programm' },
  ];

  protected readonly rangeLabel = computed<string>(() => {
    const w = this.viewState.window();
    if (!w) {
      return '—';
    }
    return `${datePart(w.from)} … ${datePart(w.to)}`;
  });

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
