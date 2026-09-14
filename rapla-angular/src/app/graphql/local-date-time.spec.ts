import { describe, expect, it } from 'vitest';
import { toLocalDateTime } from './local-date-time';

/**
 * Tier-5 — regression for the false "zwischenzeitlich geändert" warning:
 * rapla timestamps carry MILLISECONDS (DateTools.toLocalDateTime) and the
 * server's concurrency check is LocalDateTime.equals(). Stripping the
 * fraction along with the offset made every expectedLastChanged mismatch.
 * Only the offset may be removed; the fraction must survive.
 */
describe('toLocalDateTime', () => {
  it('strips the UTC offset but KEEPS the fractional seconds', () => {
    expect(toLocalDateTime('2026-07-07T02:30:15.123Z')).toBe('2026-07-07T02:30:15.123');
  });

  it('strips numeric offsets', () => {
    expect(toLocalDateTime('2026-07-07T02:30:15.5+02:00')).toBe('2026-07-07T02:30:15.5');
    expect(toLocalDateTime('2026-07-07T02:30:15-05:30')).toBe('2026-07-07T02:30:15');
  });

  it('passes through fraction-less values and null', () => {
    expect(toLocalDateTime('2026-07-07T02:30:15Z')).toBe('2026-07-07T02:30:15');
    expect(toLocalDateTime(null)).toBeNull();
  });
});
