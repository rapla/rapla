import { Injectable } from '@angular/core';

const KEY = 'rapla.lastView';

/**
 * Persists the last-opened view name across sessions (localStorage). The default
 * landing route ({@code ''} / {@code '**'}) restores it via {@link pickLandingView};
 * the {@link ViewHostComponent} writes it whenever a view opens. Storage access
 * is wrapped — a private-mode / disabled-storage browser must not break
 * navigation, so failures degrade to "no persistence" rather than throwing.
 */
@Injectable({ providedIn: 'root' })
export class LastViewStore {
  get(): string | null {
    try {
      return localStorage.getItem(KEY);
    } catch {
      return null;
    }
  }

  set(viewName: string): void {
    try {
      localStorage.setItem(KEY, viewName);
    } catch {
      // best-effort: navigation still works without persistence
    }
  }
}
