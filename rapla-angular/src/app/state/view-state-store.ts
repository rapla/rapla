import { Injectable, inject, signal } from '@angular/core';
import { type ViewRenderMode } from '../graphql/graphql.service';

import { AuthService } from '../auth/auth.service';
import { ScopedStorage, bindPerUser } from './persist';

export type { ViewRenderMode as RenderMode };

const RENDER_MODE_KEY = 'rapla.renderMode';
const WINDOW_KEY = 'rapla.window';

/** A zoneless LocalDateTime window (e.g. {@code 2026-06-21T00:00:00}) for the ReservationFilter. */
export interface DateWindow {
  from: string;
  to: string;
}

/**
 * Ephemeral per-session view state (PRD 077 Level 3): which render mode, which
 * date window, which view definition is active. Supplies the GraphQL
 * {@code $filter} variables; persisted Saved Views (Level 2) are deferred.
 */
@Injectable({ providedIn: 'root' })
export class ViewStateStore {
  private readonly auth = inject(AuthService);
  private readonly modeStorage = new ScopedStorage(this.auth, RENDER_MODE_KEY);
  private readonly windowStorage = new ScopedStorage(this.auth, WINDOW_KEY);

  // The user's EXPLICIT render-mode choice, remembered across reloads (null = none
  // yet → a view uses its own default). window is likewise restored so the chosen
  // date survives a reload. Both are per-user namespaced (PRD 089 D2). renderModes
  // is server-derived per view — not stored.
  private readonly _userMode = signal<ViewRenderMode | null>(
    this.modeStorage.load<ViewRenderMode | null>(null),
  );
  private readonly _renderMode = signal<ViewRenderMode>(this._userMode() ?? 'table');
  private readonly _window = signal<DateWindow | null>(this.windowStorage.load<DateWindow | null>(null));
  private readonly _activeView = signal<string | null>(null);
  /** Render modes supported by the active view — emitted by the server via
   *  {@code extensions.view.renderModes}. Drives which toggle buttons are shown. */
  private readonly _renderModes = signal<ViewRenderMode[]>(['table']);
  /** Result summary of the active view ("68 Termine · 5 Tage") — published by the
   *  view host after each load, shown right-aligned in the control strip. Ephemeral. */
  private readonly _resultInfo = signal<string | null>(null);

  readonly renderMode = this._renderMode.asReadonly();
  readonly window = this._window.asReadonly();
  readonly activeView = this._activeView.asReadonly();
  readonly renderModes = this._renderModes.asReadonly();
  readonly resultInfo = this._resultInfo.asReadonly();

  constructor() {
    bindPerUser(this.auth, () => {
      const mode = this.modeStorage.load<ViewRenderMode | null>(null);
      this._userMode.set(mode);
      this._renderMode.set(mode ?? 'table');
      this._window.set(this.windowStorage.load<DateWindow | null>(null));
    });
  }

  /** User picks a mode (control strip) → current + remembered choice. */
  setRenderMode(mode: ViewRenderMode): void {
    this._renderMode.set(mode);
    this._userMode.set(mode);
    this.modeStorage.save(mode);
  }

  setRenderModes(modes: ViewRenderMode[]): void {
    this._renderModes.set(modes.length ? modes : ['table']);
  }

  /** Apply a view's supported modes (from the server): keep the user's remembered
   *  mode when this view supports it, else fall to the view's default (first mode). */
  applyViewModes(modes: ViewRenderMode[]): void {
    const supported = modes.length ? modes : (['table'] as ViewRenderMode[]);
    this._renderModes.set(supported);
    const pref = this._userMode();
    this._renderMode.set(pref && supported.includes(pref) ? pref : supported[0]);
  }

  setWindow(window: DateWindow): void {
    this._window.set(window);
    this.windowStorage.save(window);
  }

  setActiveView(key: string): void {
    this._activeView.set(key);
  }

  setResultInfo(info: string | null): void {
    this._resultInfo.set(info);
  }
}
