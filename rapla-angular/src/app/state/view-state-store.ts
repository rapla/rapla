import { Injectable, inject, signal } from '@angular/core';
import { type ViewRenderMode } from '../graphql/graphql.service';

import { AuthService } from '../auth/auth.service';
import { ScopedStorage, bindPerUser } from './persist';

export type { ViewRenderMode as RenderMode };

const RENDER_MODE_KEY = 'rapla.renderMode';
const WINDOW_KEY = 'rapla.window';
const WEEK_RASTER_KEY = 'rapla.weekRaster';
/** PRD 128 D7 — the mode last chosen in each view. */
const MODE_BY_VIEW_KEY = 'rapla.renderModeByView';

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
  private readonly rasterStorage = new ScopedStorage(this.auth, WEEK_RASTER_KEY);
  private readonly byViewStorage = new ScopedStorage(this.auth, MODE_BY_VIEW_KEY);
  private modeByView = this.byViewStorage.load<Record<string, ViewRenderMode>>({});
  /** The view the last {@link applyViewModes} was for — where {@link setRenderMode} remembers the choice. */
  private currentView: string | null = null;

  // The user's EXPLICIT render-mode choice, remembered across reloads (null = none
  // yet → a view uses its own default). window is likewise restored so the chosen
  // date survives a reload. Both are per-user namespaced (PRD 089 D2). renderModes
  // is server-derived per view — not stored.
  private readonly _userMode = signal<ViewRenderMode | null>(
    this.modeStorage.load<ViewRenderMode | null>(null),
  );
  private readonly _renderMode = signal<ViewRenderMode>(this._userMode() ?? 'table');
  private readonly _window = signal<DateWindow | null>(
    this.windowStorage.load<DateWindow | null>(null),
  );
  /** Render modes supported by the active view — emitted by the server via
   *  {@code extensions.view.renderModes}. Drives which toggle buttons are shown. */
  private readonly _renderModes = signal<ViewRenderMode[]>(['table']);
  /** Result summary of the active view ("68 Termine · 5 Tage") — published by the
   *  view host after each load, shown right-aligned in the control strip. Ephemeral. */
  private readonly _resultInfo = signal<string | null>(null);

  /** Week-grid slot raster (Swing "rows per hour"): 1 = 60m, 2 = 30m, 4 = 15m.
   *  Persisted per user — the grid component remounts on every view load. */
  private readonly _weekRaster = signal<number>(this.rasterStorage.load(2));

  readonly renderMode = this._renderMode.asReadonly();
  readonly weekRaster = this._weekRaster.asReadonly();
  readonly window = this._window.asReadonly();
  readonly renderModes = this._renderModes.asReadonly();
  readonly resultInfo = this._resultInfo.asReadonly();

  constructor() {
    bindPerUser(this.auth, () => {
      const mode = this.modeStorage.load<ViewRenderMode | null>(null);
      this._userMode.set(mode);
      this._renderMode.set(mode ?? 'table');
      this._window.set(this.windowStorage.load<DateWindow | null>(null));
      this._weekRaster.set(this.rasterStorage.load(2));
      this.modeByView = this.byViewStorage.load<Record<string, ViewRenderMode>>({});
    });
  }

  setWeekRaster(rowsPerHour: number): void {
    this._weekRaster.set(rowsPerHour);
    this.rasterStorage.save(rowsPerHour);
  }

  /** User picks a mode (control strip) → current + remembered choice. */
  setRenderMode(mode: ViewRenderMode): void {
    this._renderMode.set(mode);
    this._userMode.set(mode);
    this.modeStorage.save(mode);
    if (this.currentView) {
      this.modeByView = { ...this.modeByView, [this.currentView]: mode };
      this.byViewStorage.save(this.modeByView);
    }
  }

  setRenderModes(modes: ViewRenderMode[]): void {
    this._renderModes.set(modes.length ? modes : ['table']);
  }

  /** PRD 128 D7 — entering a view: its remembered or default mode applies at once, before the first query (a date
   *  jump in an empty Prüfen selection must already see Tag); {@link applyViewModes} validates it later. */
  enterView(view: string, defaultMode?: ViewRenderMode | null): void {
    this.currentView = view;
    const mode = this.modeByView[view] ?? defaultMode;
    if (mode) this._renderMode.set(mode);
  }

  /** Apply a view's supported modes (from the server): the mode last chosen in this view, else the view's
   *  `defaultRenderMode` (PRD 128 D7), else the user's remembered mode when this view supports it; with no
   *  choice at all open week when the view offers it (user, 2026-09-15), else the view's first mode. */
  applyViewModes(
    modes: ViewRenderMode[],
    view: string | null = null,
    defaultMode?: ViewRenderMode,
  ): void {
    const supported = modes.length ? modes : (['table'] as ViewRenderMode[]);
    this._renderModes.set(supported);
    this.currentView = view;
    const fallback =
      defaultMode && supported.includes(defaultMode) ? defaultMode : this._userMode();
    const pref = (view ? this.modeByView[view] : undefined) ?? fallback;
    if (pref && supported.includes(pref)) this._renderMode.set(pref);
    else if (!pref && supported.includes('week')) this._renderMode.set('week');
    else this._renderMode.set(supported[0]);
  }

  setWindow(window: DateWindow): void {
    this._window.set(window);
    this.windowStorage.save(window);
  }

  setResultInfo(info: string | null): void {
    this._resultInfo.set(info);
  }
}
