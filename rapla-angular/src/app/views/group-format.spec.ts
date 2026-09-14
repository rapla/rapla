import { describe, it, expect } from 'vitest';
import { formatGroupLabel } from './group-format';

describe('formatGroupLabel', () => {
  it('formats "EE dd.MM" → short weekday + day.month (2026-06-15 is Montag)', () => {
    expect(formatGroupLabel('2026-06-15', 'EE dd.MM')).toBe('Mo 15.06');
  });

  it('EEEE → full German weekday', () => {
    expect(formatGroupLabel('2026-06-17', 'EEEE')).toBe('Mittwoch');
  });

  it('dd.MM.yyyy → padded date', () => {
    expect(formatGroupLabel('2026-06-05', 'dd.MM.yyyy')).toBe('05.06.2026');
  });

  it('MMM → short German month, d → unpadded day', () => {
    expect(formatGroupLabel('2026-06-05', 'd. MMM')).toBe('5. Jun');
  });

  it('passes literals (spaces, dots) through verbatim', () => {
    expect(formatGroupLabel('2026-06-15', 'EE, dd.MM.')).toBe('Mo, 15.06.');
  });

  it('returns the raw value when it is not a date (non-date group columns)', () => {
    expect(formatGroupLabel('Sonstiges', 'EE dd.MM')).toBe('Sonstiges');
  });

  it('handles a full LocalDateTime value (uses the date part)', () => {
    expect(formatGroupLabel('2026-06-21T09:00:00', 'EE')).toBe('So'); // Sonntag
  });
});
