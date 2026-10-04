import { Injectable, inject, signal } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { ScopedStorage, bindPerUser } from './persist';

export const NAV_WIDTH_DEFAULT = 290;

export function clampNavWidth(width: number): number {
  return Math.min(600, Math.max(220, Math.round(width)));
}

/** PRD 127 — the width of the left rail, resizable like Swing's JSplitPane, per user like the FilterStore. */
@Injectable({ providedIn: 'root' })
export class NavWidthStore {
  private readonly storage = new ScopedStorage(inject(AuthService), 'rapla.navWidth');
  private readonly _width = signal(this.saved());

  readonly width = this._width.asReadonly();

  constructor() {
    bindPerUser(inject(AuthService), () => this._width.set(this.saved()));
  }

  set(width: number): void {
    this._width.set(clampNavWidth(width));
    this.storage.save(this._width());
  }

  reset(): void {
    this.set(NAV_WIDTH_DEFAULT);
  }

  private saved(): number {
    return clampNavWidth(this.storage.load<number>(NAV_WIDTH_DEFAULT));
  }
}
