/**
 * PRD 091 Phase 2.1 — pure mapping of a GraphQL mutation envelope to a
 * discriminated result. No Angular imports (tier-5 testable standalone).
 * Error taxonomy: PRD 056 (`extensions.code` + `extensions.path` via the
 * server's MutationExceptionResolver).
 */

/** A single GraphQL error entry (per the spec's {@code errors[]} shape). */
export interface GqlErrorShape {
  message: string;
  path?: (string | number)[];
  extensions?: { code?: string; [k: string]: unknown };
}

export interface MutationIssue {
  code: string;
  path: string;
  message: string;
}

/**
 * - `ok` — data present, no errors.
 * - `concurrent` — CONCURRENT_MODIFICATION (stale `expectedLastChanged`) →
 *   reload-and-reapply dialog (UC-E15).
 * - `denied` — PERMISSION_DENIED / FORBIDDEN / UNAUTHENTICATED.
 * - `invalid` — every other typed validation code (REQUIRED, INVALID_VALUE,
 *   CONFLICT, ID_COLLISION, …). ID_COLLISION on the draft's OWN id is the
 *   retry-idempotency signal (PRD 056 §9) — the DRAFT layer maps it, not here.
 * - `transport` — non-2xx / network failure.
 */
export type MutationResult<T> =
  | { kind: 'ok'; data: T }
  | { kind: 'concurrent'; issues: MutationIssue[] }
  | { kind: 'denied'; issues: MutationIssue[] }
  | { kind: 'invalid'; issues: MutationIssue[] }
  | { kind: 'transport'; message: string };

const DENIED_CODES = new Set(['PERMISSION_DENIED', 'FORBIDDEN', 'UNAUTHENTICATED']);

export function toMutationResult<T>(resp: {
  data?: T;
  errors?: GqlErrorShape[];
}): MutationResult<T> {
  const errors = resp.errors ?? [];
  if (errors.length === 0 && resp.data != null) {
    return { kind: 'ok', data: resp.data };
  }
  const issues: MutationIssue[] = errors.map((e) => ({
    code: typeof e.extensions?.['code'] === 'string' ? (e.extensions['code'] as string) : 'UNKNOWN',
    path: typeof e.extensions?.['path'] === 'string' ? (e.extensions['path'] as string) : '',
    message: e.message,
  }));
  if (issues.some((i) => i.code === 'CONCURRENT_MODIFICATION')) {
    return { kind: 'concurrent', issues };
  }
  if (issues.length > 0 && issues.every((i) => DENIED_CODES.has(i.code))) {
    return { kind: 'denied', issues };
  }
  if (issues.length > 0) {
    return { kind: 'invalid', issues };
  }
  return { kind: 'transport', message: 'empty GraphQL response (no data, no errors)' };
}
