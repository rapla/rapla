import { beforeEach } from 'vitest';

// Persisted view state (scope chips, render mode, date window) lives in
// localStorage (state/persist.ts). Clear it before each test so persisted state
// never leaks between tests and the stores start from their documented defaults.
beforeEach(() => {
  try {
    localStorage.clear();
  } catch {
    // jsdom without storage — nothing to clear.
  }
});
