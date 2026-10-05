import { localeId } from '../i18n/i18n.service';

/**
 * Client-side interpreter for the server's opaque {@code groupFormat} token
 * (e.g. {@code "EE dd.MM"}) — formats a group's date value into its section
 * header. Supported tokens (java.time-ish, German locale, UTC-stable):
 *
 * - {@code E}/{@code EE}/{@code EEE} → short weekday (Mo, Di, …); {@code EEEE} → full (Montag)
 * - {@code d} → day; {@code dd} → zero-padded day
 * - {@code M} → month; {@code MM} → padded; {@code MMM} → short name (Jun); {@code MMMM} → full (Juni)
 * - {@code yy} → 2-digit year; {@code yyyy} → full year
 * - {@code HH} → hour, {@code mm} → minute (00 for a date-only value; PRD 128 column format)
 *
 * Any other character is a literal. A value that is not a {@code YYYY-MM-DD}
 * (optionally with time) date is returned verbatim — so non-date group columns
 * (already display-ready strings) pass through untouched.
 */

// Intl over a fixed reference date (2024-01-07 is a Sunday), so index 0 = Sunday; a trailing '.' is dropped.
const name = (date: Date, opts: Intl.DateTimeFormatOptions): string =>
  new Intl.DateTimeFormat(localeId(), { ...opts, timeZone: 'UTC' }).format(date).replace(/\.$/, '');
export const weekdayName = (dow: number, style: 'short' | 'long'): string =>
  name(new Date(Date.UTC(2024, 0, 7 + dow)), { weekday: style });
const monthName = (month: number, style: 'short' | 'long'): string =>
  name(new Date(Date.UTC(2024, month - 1, 1)), { month: style });

interface ParsedDate {
  year: number;
  month: number; // 1-12
  day: number;
  dow: number; // 0=So … 6=Sa (UTC)
  hour: number;
  minute: number;
}

function parseDate(value: string): ParsedDate | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  const utc = new Date(Date.UTC(year, month - 1, day));
  if (utc.getUTCMonth() !== month - 1 || utc.getUTCDate() !== day) return null; // e.g. 02-31
  const time = /T(\d{2}):(\d{2})/.exec(value);
  return {
    year,
    month,
    day,
    dow: utc.getUTCDay(),
    hour: time ? Number(time[1]) : 0,
    minute: time ? Number(time[2]) : 0,
  };
}

const pad2 = (n: number): string => String(n).padStart(2, '0');

function token(letter: string, len: number, d: ParsedDate): string {
  switch (letter) {
    case 'E':
      return weekdayName(d.dow, len >= 4 ? 'long' : 'short');
    case 'd':
      return len >= 2 ? pad2(d.day) : String(d.day);
    case 'M':
      if (len >= 4) return monthName(d.month, 'long');
      if (len === 3) return monthName(d.month, 'short');
      return len === 2 ? pad2(d.month) : String(d.month);
    case 'y':
      return len === 2 ? pad2(d.year % 100) : String(d.year);
    case 'H':
      return len >= 2 ? pad2(d.hour) : String(d.hour);
    case 'm':
      return len >= 2 ? pad2(d.minute) : String(d.minute);
    default:
      return letter.repeat(len);
  }
}

export function formatGroupLabel(value: string, pattern: string): string {
  const d = parseDate(value);
  if (!d) return value;
  let out = '';
  let i = 0;
  while (i < pattern.length) {
    const c = pattern[i];
    if ('EdMyHm'.includes(c)) {
      let j = i;
      while (j < pattern.length && pattern[j] === c) j++;
      out += token(c, j - i, d);
      i = j;
    } else {
      out += c;
      i++;
    }
  }
  return out;
}
