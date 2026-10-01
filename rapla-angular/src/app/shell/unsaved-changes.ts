import { DOCUMENT, DestroyRef, Injectable, type Signal, inject } from '@angular/core';

import { t as tr } from '../i18n/i18n.service';

/**
 * PRD 125 — open editors register their dirty() signal; while any is dirty the browser
 * asks before a reload or close (the reload dialog's "Neu laden", F5, closing the tab).
 */
@Injectable({ providedIn: 'root' })
export class UnsavedChangesService {
  private readonly sources = new Set<Signal<boolean>>();
  private readonly view = inject(DOCUMENT).defaultView;
  /** Set by {@link reload}: the user already confirmed, the browser must not ask again. */
  private confirmed = false;

  constructor() {
    this.view?.addEventListener('beforeunload', (event) => {
      if (this.confirmed || !this.any()) return;
      event.preventDefault();
      event.returnValue = '';
    });
  }

  any(): boolean {
    return [...this.sources].some((dirty) => dirty());
  }

  /** Ask before a deliberate page change (a user switch) drops open edits; true when nothing is dirty. */
  confirmDiscard(): boolean {
    return !this.any() || (this.view?.confirm(tr('unsaved_switch_confirm')) ?? true);
  }

  /** Reload after {@link confirmDiscard} — without a second browser question. */
  reload(): void {
    this.confirmed = true;
    this.view?.location.reload();
  }

  register(dirty: Signal<boolean>, destroyRef: DestroyRef): void {
    this.sources.add(dirty);
    destroyRef.onDestroy(() => this.sources.delete(dirty));
  }
}
