/**
 * Client-side interpreter for the server's opaque {@code groupFormat} token
 * (e.g. {@code "EE dd.MM"}) — formats a group's date value into its section
 * header. Supported tokens (java.time-ish, German locale, UTC-stable):
 *
 * - {@code E}/{@code EE}/{@code EEE} → short weekday (Mo, Di, …); {@code EEEE} → full (Montag)
 * - {@code d} → day; {@code dd} → zero-padded day
 * - {@code M} → month; {@code MM} → padded; {@code MMM} → short name (Jun); {@code MMMM} → full (Juni)
 * - {@code yy} → 2-digit year; {@code yyyy} → full year
 *
 * Any other character is a literal. A value that is not a {@code YYYY-MM-DD}
 * (optionally with time) date is returned verbatim — so non-date group columns
 * (already display-ready strings) pass through untouched.
 */

const WEEKDAY_SHORT = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
const WEEKDAY_FULL = [
  'Sonntag',
  'Montag',
  'Dienstag',
  'Mittwoch',
  'Donnerstag',
  'Freitag',
  'Samstag',
];
const MONTH_SHORT = [
  'Jan',
  'Feb',
  'Mär',
  'Apr',
  'Mai',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Okt',
  'Nov',
  'Dez',
];
const MONTH_FULL = [
  'Januar',
  'Februar',
  'März',
  'April',
  'Mai',
  'Juni',
  'Juli',
  'August',
  'September',
  'Oktober',
  'November',
  'Dezember',
];

interface ParsedDate {
  year: number;
  month: number; // 1-12
  day: number;
  dow: number; // 0=So … 6=Sa (UTC)
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
  return { year, month, day, dow: utc.getUTCDay() };
}

const pad2 = (n: number): string => String(n).padStart(2, '0');

function token(letter: string, len: number, d: ParsedDate): string {
  switch (letter) {
    case 'E':
      return len >= 4 ? WEEKDAY_FULL[d.dow] : WEEKDAY_SHORT[d.dow];
    case 'd':
      return len >= 2 ? pad2(d.day) : String(d.day);
    case 'M':
      if (len >= 4) return MONTH_FULL[d.month - 1];
      if (len === 3) return MONTH_SHORT[d.month - 1];
      return len === 2 ? pad2(d.month) : String(d.month);
    case 'y':
      return len === 2 ? pad2(d.year % 100) : String(d.year);
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
    if ('EdMy'.includes(c)) {
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
