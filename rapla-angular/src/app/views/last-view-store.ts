import { Injectable, inject } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { userScopeSuffix } from '../state/persist';

const KEY = 'rapla.lastView';

/**
 * Persists the last-opened view name across sessions (localStorage). The default
 * landing route ({@code ''} / {@code '**'}) restores it via {@link pickLandingView};
 * the {@link ViewHostComponent} writes it whenever a view opens. Storage access
 * is wrapped — a private-mode / disabled-storage browser must not break
 * navigation, so failures degrade to "no persistence" rather than throwing.
 *
 * PRD 089 D2: the key is per-user namespaced ({@code rapla.lastView::u=<userId>})
 * so two rapla accounts on one browser don't restore each other's last view.
 */
@Injectable({ providedIn: 'root' })
export class LastViewStore {
  private readonly auth = inject(AuthService);

  private scopedKey(): string {
    return `${KEY}${userScopeSuffix(this.auth)}`;
  }

  get(): string | null {
    try {
      return localStorage.getItem(this.scopedKey());
    } catch {
      return null;
    }
  }

  set(viewName: string): void {
    try {
      localStorage.setItem(this.scopedKey(), viewName);
    } catch {
      // best-effort: navigation still works without persistence
    }
  }
}
