# PRD 133 — Deployment branding: SPA toolbar and favicon from the patch directory

**Status:** in-progress — 2026-10-14 (implemented, browser check after the dev-server restart pending)
**Related:** [PRD 112](done/112-deployment-patch.md) (patch directory), [PRD 131](131-logout-stay-signed-in.md) (`login.css` / `login.mustache` from the same directory)

## Abstract

A deployment brands the SPA toolbar and the favicon of SPA, login page and start page without a rebuild: two files in `data/patch/rapla/`, next to `login.css`. Without them everything looks as before, except that the SPA now shows the Rapla icon and the title "Rapla" instead of the Angular defaults.

## Implementation

- `SpaResourceConfig`: resource handlers for exactly `/favicon.ico`, `/app/favicon.ico`, `/images/favicon.ico` (`<patch-dir>/rapla/`, then `classpath:/static/images/`) and `/app/spa.css` (`<patch-dir>/rapla/`, then the SPA build's empty `spa.css`); `/patch/{ns}/{file}` serves `<patch-dir>/<ns>/` for css/svg/png/jpg/ico/woff2 only (D1); `SecurityConfig` permits `/patch/**`, `Cache-Control: no-cache`. Spring's resource handler re-reads per request with `Last-Modified`, so no `PatchFile` cache is needed.
- `SecurityConfig`: `/favicon.ico` permitted (was 401 without a session; the login page has no icon link, so the browser asks for `/favicon.ico`).
- SPA: `index.html` links `spa.css` and is titled "Rapla"; `public/favicon.ico` = the Rapla icon; `theme.scss` / `app-toolbar` / `omnibox` read the toolbar colours from CSS variables whose defaults are today's values; `.app-title { content: var(--brand-logo, normal) }` swaps the word mark for the logo.
- `proxy.conf.js`: `/app/spa.css`, `/app/favicon.ico`, `/favicon.ico` go to :8051, so `ng serve` shows the server's patch files instead of `public/`.
- 8051 serves the SPA from `rapla-app/target/classes/static/app` — a `-am compile` restart does not rebuild it; the branding needs a fresh SPA build there.
- Start page and status page (2026-10-15): built-in `templates/index.mustache` / `status.mustache` in rapla-server, overridable from `<patch-dir>/rapla/`; one shared `index.css` inlined on both as `<style id="patch-index-css">`, an empty `.logo` div first in `body` like the login page. `PatchablePage` (rapla-server) is the one implementation behind login, start and status page and resolves every page file under `<patch-dir>/rapla/`, so `ArtifactPatchLoader` (top level only) needs no exception list. Both pages now send UTF-8 (was ISO-8859-1) so patch files may use any character.
- Variables and file names: [docs/deployment.md § Patch directory](../deployment.md#patch-directory--stored-views-and-documents-from-files-prd-112).
- A deployment's own templates live in its own repository, not here. A coloured full-height "New" block was tried and dropped (user 2026-10-14: "New" as in the classic toolbar, next to the search field); the `--new-*` variables stay available.

## Goal

With a deployment's files in `data/patch/rapla/`: `/favicon.ico` (no cookie), `/app/favicon.ico`, `/images/favicon.ico` return its icon, `/app/spa.css` its variables, the toolbar shows its colours and logo with the classic "New" button next to the search field; `/app/<view>.graphql` still returns the SPA shell, never a patch file.

## Scope

### In scope
- Favicon for SPA, login page, start page and HTML calendars; SPA toolbar colours, logo, "New" button.
- Start and status page: `index.css`, `index.mustache`, `status.mustache`.

### Out of scope
- TEST marker for test systems (user 2026-10-14: "skip the test marker for now"; works as a different logo file in that system's `spa.css`).
- SPA branding beyond the toolbar.

## Plan

- [x] Favicon routes + `/favicon.ico` permit
- [x] `/app/spa.css` route, SPA variables, logo slot, empty default `spa.css`
- [x] Templates for the first deployment (in its own repository)
- [ ] Browser check on a restarted dev server with a deployment's patch files (toolbar + favicon screenshot)

## Tests

No new automated tests for the SPA part (user 2026-10-14: "du kannst die tests weglassen"). Start/status page and namespaces: `StartStatusPageTemplateTest`, `PatchResourceTest` (tier 3), `ArtifactPatchLoaderTest` fixture. Verified by `ng test` and a curl probe of the four routes plus a `.graphql` name after the restart.

## Decisions locked

**D1 — namespace folders, types allowlisted (user 2026-10-15, replaces "exact names only").** The top level of the patch dir holds stored views and documents and is never served. Files the core reads by fixed name live in `<patch-dir>/rapla/` (page CSS/templates, `spa.css`, `favicon.ico`); every folder `<patch-dir>/<ns>/` is served as `/patch/<ns>/<file>` without login, but only css/svg/png/jpg/ico/woff2, so a template or view in it stays unreachable. No special names at the top level: the old loader exception list is gone, a document may be called `login`. `rapla/` is the core's; `local/` by convention the deployment's own assets. Plugin-JAR layer and namespaced views: not built until a consumer exists. Alternative rejected: an extra `web/` level (the type allowlist gives the same safety and leaves room for views per namespace).

**D2 — variables, not selectors.** `spa.css` sets custom properties on `:root`; the SPA's own rules read them with today's values as fallback, so load order and Angular's style encapsulation don't matter. Alternative rejected: a stylesheet overriding component classes (fights `_ngcontent` specificity, breaks silently on refactors).

**D3 — resource handler, not a controller with `PatchFile`.** Spring's resource handling already re-reads the file per request and sends `Last-Modified`/content type; a controller would also need an `ApiPrefixArchitectureTest` exemption.
