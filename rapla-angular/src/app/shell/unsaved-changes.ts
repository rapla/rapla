import { DOCUMENT, DestroyRef, Injectable, type Signal, inject } from '@angular/core';

/**
 * PRD 125 — open editors register their dirty() signal; while any is dirty the browser
 * asks before a reload or close (the reload dialog's "Neu laden", F5, closing the tab).
 */
@Injectable({ providedIn: 'root' })
export class UnsavedChangesService {
  private readonly sources = new Set<Signal<boolean>>();

  constructor() {
    inject(DOCUMENT).defaultView?.addEventListener('beforeunload', (event) => {
      if (!this.any()) return;
      event.preventDefault();
      event.returnValue = '';
    });
  }

  any(): boolean {
    return [...this.sources].some((dirty) => dirty());
  }

  register(dirty: Signal<boolean>, destroyRef: DestroyRef): void {
    this.sources.add(dirty);
    destroyRef.onDestroy(() => this.sources.delete(dirty));
  }
}
