/**
 * Tiny localStorage helpers for the per-session view state we remember across
 * reloads (scope chips, render mode, date window, last view). Wrapped so a
 * private-mode / disabled-storage browser degrades to "no persistence" instead
 * of throwing.
 *
 * PRD 089 D2: these keys are PER-USER NAMESPACED — the stored key is suffixed
 * with {@code ::u=<userId>} so two rapla accounts on one browser don't bleed
 * each other's scope chips / render mode / window / last view. (Recents and
 * favorites moved to server storage entirely — see RecentsFavoritesService —
 * and must NOT use this helper.) Security here is explicitly not a goal (same
 * physical person, different accounts); namespacing closes the cross-account
 * bleed cheaply.
 */

import { effect } from '@angular/core';

import { AuthService } from '../auth/auth.service';

export function loadJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function saveJson(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // best-effort: navigation still works without persistence
  }
}

/** The current user's storage suffix — {@code ::u=<userId>}, or {@code ::u=anon}
 *  when unauthenticated. */
export function userScopeSuffix(auth: AuthService): string {
  return `::u=${auth.identity()?.userId ?? 'anon'}`;
}

/**
 * Per-user namespaced localStorage for a single base key (PRD 089 D2). The
 * physical key is {@code <base>::u=<userId>}, resolved from {@link AuthService}
 * at every read/write — so switching identity reads a different slot and the
 * accounts stay isolated.
 */
export class ScopedStorage {
  constructor(
    private readonly auth: AuthService,
    private readonly baseKey: string,
  ) {}

  /** The physical, user-suffixed localStorage key for the current identity. */
  scopedKey(): string {
    return `${this.baseKey}${userScopeSuffix(this.auth)}`;
  }

  load<T>(fallback: T): T {
    return loadJson<T>(this.scopedKey(), fallback);
  }

  save(value: unknown): void {
    saveJson(this.scopedKey(), value);
  }
}

/**
 * Reload a per-user signal whenever the identity changes. {@code read} re-reads
 * the (now user-scoped) value from storage; {@code apply} pushes it into the
 * store's signal. The effect fires once on registration and again on every
 * identity flip, so switching accounts immediately swaps in that account's
 * value (or its fallback) — the {@code bindPerUser} reload-on-identity-change.
 *
 * Must be called from an injection context (a store constructor).
 */
export function bindPerUser(auth: AuthService, read: () => void): void {
  let lastUserId: string | null | undefined = undefined;
  effect(() => {
    const userId = auth.identity()?.userId ?? null;
    if (userId === lastUserId) return;
    lastUserId = userId;
    read();
  });
}
