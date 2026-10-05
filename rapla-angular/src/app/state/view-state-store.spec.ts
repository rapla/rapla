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

  it('defaults to the table render mode and no window', () => {
    expect(store.renderMode()).toBe('table');
    expect(store.window()).toBeNull();
  });

  it('setRenderMode() switches the render mode', () => {
    store.setRenderMode('week');
    expect(store.renderMode()).toBe('week');
  });

  it('setWindow() stores the date window verbatim', () => {
    store.setWindow({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
    expect(store.window()).toEqual({ from: '2026-06-15T00:00:00', to: '2026-06-22T00:00:00' });
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

  it('remembers the week raster (rows per hour) across a reload', () => {
    expect(store.weekRaster()).toBe(2);
    store.setWeekRaster(4);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [ViewStateStore, AuthService] });
    const reloaded = TestBed.inject(ViewStateStore);
    expect(reloaded.weekRaster()).toBe(4);
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

  it('applyViewModes opens week when no mode was ever chosen, even if the view lists table first', () => {
    store.applyViewModes(['table', 'day', 'week', 'month']); // e.g. Termine
    expect(store.renderMode()).toBe('week');
  });

  it('applyViewModes keeps the view default when no mode was chosen and the view has no week', () => {
    store.applyViewModes(['table', 'month']);
    expect(store.renderMode()).toBe('table');
  });

  describe('PRD 128 D7 — default render mode per view, remembered per view', () => {
    const ALL = ['table', 'day', 'week', 'month'] as const;

    it('the first open of a view with defaultRenderMode uses it, even over a remembered mode', () => {
      store.setRenderMode('week');
      store.applyViewModes([...ALL], 'rapla_conflicts', 'day');
      expect(store.renderMode()).toBe('day');
    });

    it('a mode chosen in that view sticks to it; other views keep theirs', () => {
      store.applyViewModes([...ALL], 'rapla_appointments');
      store.setRenderMode('month');
      store.applyViewModes([...ALL], 'rapla_conflicts', 'day');
      expect(store.renderMode()).toBe('day');
      store.setRenderMode('week');
      store.applyViewModes([...ALL], 'rapla_appointments');
      expect(store.renderMode()).toBe('month');
      store.applyViewModes([...ALL], 'rapla_conflicts', 'day');
      expect(store.renderMode()).toBe('week');
    });

    it('entering a view sets its remembered or default mode before any query (date jump in an empty selection)', () => {
      store.setRenderMode('table');
      store.enterView('rapla_conflicts', 'day');
      expect(store.renderMode()).toBe('day');
      store.enterView('rapla_appointments');
      expect(store.renderMode()).toBe('day');
    });

    it('survives a reload', () => {
      store.applyViewModes([...ALL], 'rapla_conflicts', 'day');
      store.setRenderMode('month');
      TestBed.resetTestingModule();
      const fresh = TestBed.inject(ViewStateStore);
      fresh.applyViewModes([...ALL], 'rapla_conflicts', 'day');
      expect(fresh.renderMode()).toBe('month');
    });
  });
});
