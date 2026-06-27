import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { ViewStateStore } from './view-state-store';
import { AuthService } from '../auth/auth.service';

describe('ViewStateStore', () => {
  let store: ViewStateStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ViewStateStore, AuthService] });
    store = TestBed.inject(ViewStateStore);
  });

  it('defaults to the table render mode and no window/view', () => {
    expect(store.renderMode()).toBe('table');
    expect(store.window()).toBeNull();
    expect(store.activeView()).toBeNull();
  });

  it('setRenderMode() switches the render mode', () => {
    store.setRenderMode('week');
    expect(store.renderMode()).toBe('week');
  });

  it('setWindow() stores the date window verbatim', () => {
    store.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
    expect(store.window()).toEqual({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
  });

  it('setActiveView() records the active view definition key', () => {
    store.setActiveView('Termine');
    expect(store.activeView()).toBe('Termine');
  });

  it('remembers the user render mode + window across a reload (new store from localStorage)', () => {
    store.setRenderMode('week');
    store.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [ViewStateStore, AuthService] });
    const reloaded = TestBed.inject(ViewStateStore);
    expect(reloaded.renderMode()).toBe('week');
    expect(reloaded.window()).toEqual({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
  });

  it('applyViewModes keeps the remembered mode when the view supports it', () => {
    store.setRenderMode('table');
    store.applyViewModes(['week', 'table']);
    expect(store.renderMode()).toBe('table'); // remembered + supported
    expect(store.renderModes()).toEqual(['week', 'table']);
  });

  it('applyViewModes falls back to the view default when the mode is unsupported', () => {
    store.setRenderMode('week');
    store.applyViewModes(['table']); // view does not offer week
    expect(store.renderMode()).toBe('table');
  });

  it('applyViewModes uses the view default when no mode was ever chosen', () => {
    store.applyViewModes(['week', 'table']); // fresh store, no user choice
    expect(store.renderMode()).toBe('week');
  });
});
