/**
 * DateTime (offset) → the LocalDateTime string the mutations' concurrency
 * check expects. ONLY the offset is stripped — rapla timestamps carry
 * milliseconds and the server compares with LocalDateTime.equals(), so
 * dropping the fraction makes every expectedLastChanged mismatch (the false
 * "zwischenzeitlich geändert" bug, fixed 2026-07-07).
 */
export function toLocalDateTime(offsetIso: string | null): string | null {
  if (!offsetIso) return null;
  return offsetIso.replace(/(Z|[+-]\d{2}:\d{2})$/, '');
}
