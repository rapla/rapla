/**
 * PRD 078 — the shell's navigation list. Hardcoded for now; this is the SEAM
 * where a view-source feeds in later: once views are stored/authored (Modell 2,
 * {@code GET /api/views}) or held in user preferences (Modell 3), the sidenav
 * builds its entries from that source instead of this constant. Keeping it in
 * one place means the sidenav component never changes when the source lands.
 */
export interface NavItem {
  /** Display label in the sidenav. */
  label: string;
  /** SPA route path (under {@code /app}). */
  path: string;
  /** Material icon name. */
  icon: string;
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Termine', path: '/appointments', icon: 'event' },
  { label: 'Reservierungen', path: '/reservations', icon: 'table_rows' },
];
