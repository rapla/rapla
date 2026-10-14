import type { HttpInterceptorFn } from '@angular/common/http';

/**
 * PRD 131 D7/D12 — per-tab impersonation. The tab's act-as target (a user id) lives in `sessionStorage`
 * (one key): it survives a reload and ends with the tab. Every `/api` request names who it acts as; the
 * server turns a permitted id into the impersonation shape, `self` leaves the signed-in user.
 */
const KEY = 'rapla.actAs';
export const SELF = 'self';
export const ACT_AS_HEADER = 'X-Rapla-Act-As';
export const ACT_AS_PARAM = 'actAs';

export function actAsId(): string {
  try {
    return sessionStorage.getItem(KEY) || SELF;
  } catch {
    return SELF;
  }
}

/** Remember the target for this tab, or `null` to act as the signed-in user again. */
export function storeActAs(id: string | null): void {
  try {
    if (id) sessionStorage.setItem(KEY, id);
    else sessionStorage.removeItem(KEY);
  } catch {
    // storage blocked: the tab then acts as the signed-in user
  }
}

export const actAsInterceptor: HttpInterceptorFn = (req, next) =>
  req.url.startsWith('/api/')
    ? next(req.clone({ setHeaders: { [ACT_AS_HEADER]: actAsId() } }))
    : next(req);
