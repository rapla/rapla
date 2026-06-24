import { Injectable, signal } from '@angular/core';
import { type ViewRenderMode } from '../graphql/graphql.service';

export type { ViewRenderMode as RenderMode };

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
  private readonly _renderMode = signal<ViewRenderMode>('table');
  private readonly _window = signal<DateWindow | null>(null);
  private readonly _activeView = signal<string | null>(null);
  /** Render modes supported by the active view — emitted by the server via
   *  {@code extensions.view.renderModes}. Drives which toggle buttons are shown. */
  private readonly _renderModes = signal<ViewRenderMode[]>(['table']);

  readonly renderMode = this._renderMode.asReadonly();
  readonly window = this._window.asReadonly();
  readonly activeView = this._activeView.asReadonly();
  readonly renderModes = this._renderModes.asReadonly();

  setRenderMode(mode: ViewRenderMode): void {
    this._renderMode.set(mode);
  }

  setRenderModes(modes: ViewRenderMode[]): void {
    this._renderModes.set(modes.length ? modes : ['table']);
  }

  setWindow(window: DateWindow): void {
    this._window.set(window);
  }

  setActiveView(key: string): void {
    this._activeView.set(key);
  }
}
