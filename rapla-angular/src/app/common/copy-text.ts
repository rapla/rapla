/**
 * Copy text to the clipboard. `navigator.clipboard` exists only in secure contexts (https,
 * localhost) — over plain `http://<ip>` it is undefined (customer intranet over plain http, 2026-09-30), so fall back to
 * the legacy selection + `execCommand('copy')` path. Resolves true when something was copied.
 */
export async function copyText(text: string): Promise<boolean> {
  // eslint-disable-next-line no-restricted-properties -- the one sanctioned use, guarded
  if (navigator.clipboard?.writeText) {
    try {
      // eslint-disable-next-line no-restricted-properties
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // fall through to the legacy path (e.g. permission denied)
    }
  }
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.setAttribute('readonly', '');
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  let ok: boolean;
  try {
    ok = document.execCommand('copy');
  } catch {
    ok = false;
  }
  ta.remove();
  return ok;
}
