import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { NAV_WIDTH_DEFAULT, NavWidthStore, clampNavWidth } from './nav-width-store';
import { AuthService } from '../auth/auth.service';

/** PRD 127 — the left rail is resizable like Swing's JSplitPane; the width persists per user. */
describe('NavWidthStore', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [NavWidthStore, AuthService] });
  });

  it('clamps to 220–600 px and rounds', () => {
    expect(clampNavWidth(100)).toBe(220);
    expect(clampNavWidth(900)).toBe(600);
    expect(clampNavWidth(333.6)).toBe(334);
  });

  it('starts at 290 px', () => {
    expect(TestBed.inject(NavWidthStore).width()).toBe(NAV_WIDTH_DEFAULT);
    expect(NAV_WIDTH_DEFAULT).toBe(290);
  });

  it('a set width is clamped, persisted and restored in a fresh store', () => {
    TestBed.inject(NavWidthStore).set(1000);
    expect(TestBed.inject(NavWidthStore).width()).toBe(600);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [NavWidthStore, AuthService] });
    expect(TestBed.inject(NavWidthStore).width()).toBe(600);
  });

  it('reset() goes back to 290 px', () => {
    const store = TestBed.inject(NavWidthStore);
    store.set(400);
    store.reset();
    expect(store.width()).toBe(290);
  });
});
