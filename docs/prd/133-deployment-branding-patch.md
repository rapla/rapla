# PRD 133 — Deployment branding: SPA toolbar and favicon from the patch directory

**Status:** in-progress — 2026-10-14 (implemented, browser check after the dev-server restart pending)
**Related:** [PRD 112](done/112-deployment-patch.md) (patch directory), [PRD 131](131-logout-stay-signed-in.md) (`login.css` / `login.mustache` from the same directory)

## Abstract

A deployment (first: DHBW) brands the SPA toolbar and the favicon of SPA, login page and start page without a rebuild: two files in `data/patch/`, next to `login.css`. Without them everything looks as before, except that the SPA now shows the Rapla icon and the title "Rapla" instead of the Angular defaults.

## Implementation

- `SpaResourceConfig`: resource handlers for exactly `/favicon.ico`, `/app/favicon.ico`, `/images/favicon.ico` (patch dir, then `classpath:/static/images/`) and `/app/spa.css` (patch dir, then the SPA build's empty `spa.css`), `Cache-Control: no-cache`. Spring's resource handler re-reads per request with `Last-Modified`, so no `PatchFile` cache is needed.
- `SecurityConfig`: `/favicon.ico` permitted (was 401 without a session; the login page has no icon link, so the browser asks for `/favicon.ico`).
- SPA: `index.html` links `spa.css` and is titled "Rapla"; `public/favicon.ico` = the Rapla icon; `theme.scss` / `app-toolbar` / `omnibox` read the toolbar colours from CSS variables whose defaults are today's values; `.app-title { content: var(--brand-logo, normal) }` swaps the word mark for the logo; the search field is `box-sizing: border-box` (it overflowed its 640 px slot by 30 px).
- Variables and file names: [docs/deployment.md § Patch directory](../deployment.md#patch-directory--stored-views-and-documents-from-files-prd-112).
- DHBW templates (variant B, dhbw.de style): dhbwrapla `docs/patch/spa.css`, `docs/patch/favicon.ico` (16/32/48 px from the Keycloak-theme signet).

## Goal

With the DHBW files in `data/patch/`: `/favicon.ico` (no cookie), `/app/favicon.ico`, `/images/favicon.ico` return the DHBW icon, `/app/spa.css` the DHBW variables, the toolbar is white with logo and red "New" block; `/app/<view>.graphql` still returns the SPA shell, never a patch file.

## Scope

### In scope
- Favicon for SPA, login page, start page and HTML calendars; SPA toolbar colours, logo, "New" button.

### Out of scope
- TEST marker for test systems (user 2026-10-14: "skip the test marker for now"; works as a different logo file in that system's `spa.css`).
- Branding beyond the toolbar.

## Plan

- [x] Favicon routes + `/favicon.ico` permit
- [x] `/app/spa.css` route, SPA variables, logo slot, empty default `spa.css`
- [x] DHBW templates in dhbwrapla
- [ ] Browser check on the restarted dhbw dev server (toolbar + favicon screenshot)

## Tests

No new automated tests (user 2026-10-14: "du kannst die tests weglassen"). Verified by `ng test` and a curl probe of the four routes plus a `.graphql` name after the restart.

## Decisions locked

**D1 — exact names only.** `data/patch/` also holds stored views and documents, so only `favicon.ico` and `spa.css` are mapped; no directory mapping.

**D2 — variables, not selectors.** `spa.css` sets custom properties on `:root`; the SPA's own rules read them with today's values as fallback, so load order and Angular's style encapsulation don't matter. Alternative rejected: a stylesheet overriding component classes (fights `_ngcontent` specificity, breaks silently on refactors).

**D3 — resource handler, not a controller with `PatchFile`.** Spring's resource handling already re-reads the file per request and sends `Last-Modified`/content type; a controller would also need an `ApiPrefixArchitectureTest` exemption.
