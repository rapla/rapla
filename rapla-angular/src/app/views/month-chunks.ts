/*
 * Portions derived from EventCalendar (https://github.com/vkurko/calendar),
 * Copyright (c) Vladimir Kurko, MIT License — see LICENSE_MIT_EVENTCALENDAR.
 * This file as a whole is part of rapla (Apache-2.0 / GPL-3.0 dual license).
 */

export interface MonthWindow {
  from: string;
  to: string;
}

export interface WeekChunk<T> {
  row: T;
  col: number;
  span: number;
  contLeft: boolean;
  contRight: boolean;
  top: number;
  bottom: number;
}

export const ITEM_H = 20,
  ITEM_GAP = 2,
  TOP0 = 26;

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

function isoOf(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function parseDate(s: string): Date {
  const [y, m, d] = s.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

function shiftDay(s: string, n: number): string {
  const d = parseDate(s);
  d.setDate(d.getDate() + n);
  return isoOf(d);
}

function daysBetween(a: string, b: string): number {
  return Math.round((parseDate(b).getTime() - parseDate(a).getTime()) / 86400000);
}

function gridRange(anchorIso: string): { start: string; end: string } {
  const [y, m] = anchorIso.slice(0, 10).split('-').map(Number);
  const first = new Date(y, m - 1, 1);
  const start = new Date(first);
  start.setDate(1 - ((first.getDay() + 6) % 7));
  const last = new Date(y, m, 0);
  const end = new Date(last);
  end.setDate(last.getDate() + ((7 - (((last.getDay() + 6) % 7) + 1)) % 7) + 1);
  return { start: isoOf(start), end: isoOf(end) };
}

/**
 * Grid window for a month: Monday on/before the 1st .. day AFTER the grid Sunday
 * (exclusive end), as LocalDateTime strings T00:00:00. anchorIso is any
 * 'YYYY-MM-DD...' string inside the month.
 */
export function monthGridWindow(anchorIso: string): MonthWindow {
  const { start, end } = gridRange(anchorIso);
  return { from: `${start}T00:00:00`, to: `${end}T00:00:00` };
}

/** The grid's weeks: arrays of 7 'YYYY-MM-DD' strings, Monday-first. */
export function monthGridDays(anchorIso: string): string[][] {
  const { start, end } = gridRange(anchorIso);
  const weeks: string[][] = [];
  for (let day = start; day < end; day = shiftDay(day, 7)) {
    const days: string[] = [];
    for (let i = 0; i < 7; i++) {
      days.push(shiftDay(day, i));
    }
    weeks.push(days);
  }
  return weeks;
}

interface BuildingChunk<T> {
  row: T;
  col: number;
  span: number;
  contLeft: boolean;
  contRight: boolean;
  top: number;
  bottom: number;
  placed: boolean;
}

/**
 * Chunk + stack the rows of ONE week. getStart/getEnd return 'YYYY-MM-DD' date
 * parts (end INCLUSIVE, i.e. the last covered day). Rows not intersecting the
 * week are ignored. Multi-day chunks are processed first (longer first), then
 * single-day rows in given order; stacking = per-start-column prev-chain, plus
 * push-down below 'long' chunks passing through the column (EventCalendar
 * algorithm).
 */
export function chunkWeek<T>(
  rows: T[],
  weekDays: string[],
  getStart: (r: T) => string,
  getEnd: (r: T) => string,
): WeekChunk<T>[] {
  const weekStart = weekDays[0];
  const weekEnd = weekDays[6];
  const chunks: BuildingChunk<T>[] = [];
  for (const row of rows) {
    const start = getStart(row);
    const end = getEnd(row);
    if (start > weekEnd || end < weekStart) {
      continue;
    }
    const from = start < weekStart ? weekStart : start;
    const to = end > weekEnd ? weekEnd : end;
    chunks.push({
      row,
      col: daysBetween(weekStart, from),
      span: daysBetween(from, to) + 1,
      contLeft: start < weekStart,
      contRight: end > weekEnd,
      top: 0,
      bottom: 0,
      placed: false,
    });
  }
  const multi = chunks.filter((c) => c.span > 1).sort((a, b) => b.span - a.span);
  const singles = chunks.filter((c) => c.span === 1);
  const ordered = [...multi, ...singles];

  const prevChunks = new Map<number, BuildingChunk<T>>();
  const longChunks = new Map<number, BuildingChunk<T>[]>();
  for (const c of ordered) {
    for (let i = 1; i < c.span; i++) {
      const col = c.col + i;
      const list = longChunks.get(col) ?? [];
      list.push(c);
      longChunks.set(col, list);
    }
    const prev = prevChunks.get(c.col);
    prevChunks.set(c.col, c);
    let top = prev ? prev.bottom + ITEM_GAP : TOP0;
    const passing = (longChunks.get(c.col) ?? [])
      .filter((lc) => lc.placed)
      .sort((a, b) => a.top - b.top);
    for (const lc of passing) {
      if (top < lc.bottom && top + ITEM_H > lc.top) {
        top = lc.bottom + ITEM_GAP;
      }
    }
    c.top = top;
    c.bottom = top + ITEM_H;
    c.placed = true;
  }
  return ordered.map(({ row, col, span, contLeft, contRight, top, bottom }) => ({
    row,
    col,
    span,
    contLeft,
    contRight,
    top,
    bottom,
  }));
}
