import { describe, it, expect, beforeEach } from 'vitest';
import { ViewStateStore } from './view-state-store';

describe('ViewStateStore', () => {
  let store: ViewStateStore;

  beforeEach(() => {
    store = new ViewStateStore();
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
});
