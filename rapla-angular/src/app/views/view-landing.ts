/**
 * Pick the landing view for the default route ({@code ''} / {@code '**'}):
 * restore the last-opened view when it is still in the catalog, else fall back
 * to the first catalog view. View names are server-seeded and deployment-
 * specific, so a stored name may no longer exist (deployment changed, view
 * un-shared, permission lost) — the fallback keeps the landing robust. Returns
 * null when the catalog is empty (no view to land on).
 */
export function pickLandingView(last: string | null, names: string[]): string | null {
  if (last && names.includes(last)) return last;
  return names[0] ?? null;
}
