import { describe, it, expect } from 'vitest';
import { pickLandingView } from './view-landing';

describe('pickLandingView', () => {
  it('restores the last-opened view when it is still in the catalog', () => {
    expect(pickLandingView('Wochenansicht', ['Raumauslastung', 'Wochenansicht'])).toBe(
      'Wochenansicht',
    );
  });

  it('falls back to the first catalog view when there is no stored view (first visit)', () => {
    expect(pickLandingView(null, ['Raumauslastung', 'Wochenansicht'])).toBe('Raumauslastung');
  });

  it('falls back to the first view when the stored name is no longer in the catalog', () => {
    // Deployment changed / view un-shared / permission lost — stored name is stale.
    expect(pickLandingView('GoneView', ['Raumauslastung', 'Wochenansicht'])).toBe('Raumauslastung');
  });

  it('lands a new user on Termine (rapla_appointments) when it is in the catalog, not on the first view', () => {
    expect(pickLandingView(null, ['Raumauslastung', 'rapla_appointments', 'rapla_kalender'])).toBe(
      'rapla_appointments',
    );
  });

  it('also lands on Termine when the stored view is no longer in the catalog', () => {
    expect(pickLandingView('GoneView', ['Raumauslastung', 'rapla_appointments'])).toBe(
      'rapla_appointments',
    );
  });

  it('keeps a returning user on their last view even when Termine exists', () => {
    expect(pickLandingView('rapla_kalender', ['rapla_appointments', 'rapla_kalender'])).toBe(
      'rapla_kalender',
    );
  });

  it('returns null when the catalog is empty (no view to land on)', () => {
    expect(pickLandingView('Wochenansicht', [])).toBeNull();
    expect(pickLandingView(null, [])).toBeNull();
  });
});
