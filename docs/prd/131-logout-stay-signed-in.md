# PRD 131 — Logout, stay-signed-in, client token storage and impersonation

**Status:** in-progress — 2026-10-08 (A–G, I implemented; review 22/22b/22c accepted, no open MEDIUM; deployed to the DHBW test system 07:53, login texts (D2a) with the 08:29 build; H/OQ9 deferred; pending: two-tab test, LOW residue, commit)
**Related:** [PRD 072](done/072-server-side-login-dialog.md) (cookie login, `/login` page, #7 refresh cap), [PRD 126](done/126-swing-sso-auto-login.md) (Swing remember consent — replaced here), [PRD 051](done/051-switch-user-with-oauth.md) (impersonation), [PRD 052](052-client-clean-restart.md) (in-JVM restart), [PRD 102](102-browser-credential-hardening.md) (no token in JS, CSP), [PRD 029](done/029-swing-oauth-login.md) (token store, OQ10 — its JNLP-first order is overturned by D3), [PRD 043](043-api-keys-jwt-pat.md)/[PRD 076](076-scoped-api-keys-self-rotation.md) (API keys), [PRD 113](done/113-graphql-permission-model.md) (permission model), [PRD 117](117-graphql-administration-entities.md) (E8/E9/PH1 group guards; category annotations over GraphQL, E11)

## Abstract

Logout becomes one visible, consistently named control next to the user in Swing and SPA. It ends the user's single session on every device. "Stay signed in" is chosen once on the `/login` page, applies to password and SSO logins alike, and Swing honours it without asking. Swing stores the refresh token only where other non-admin OS users cannot read it, and only for the server that issued it. Impersonation works per tab and per client, and members of a group may switch to selected users (e.g. a shared functional account) without being admins.

## Current state (2026-10-07)

- One refresh session per user, shared by all devices: `RefreshSessionService.obtainOrMintRefreshToken` returns the stored token while valid; `clearSession` deletes it. After logout, refresh is rejected at once (`RefreshSessionService:222-224`), but access tokens stay valid up to 1 h (`ACCESS_TOKEN_TTL_SECONDS`).
- The `/login` checkbox "Angemeldet bleiben auf diesem Gerät" sits inside the password form below the SSO links, so SSO logins never send it. It drives Spring's sliding `rapla-remember-me` cookie (`rapla.auth.remember-me-days`, 30 d) and the `rapla-remember-choice` cookie (HttpOnly, `FormLoginSuccessHandler:66`). The SPA refresh cookie is always set for 21 d (`FormLoginSuccessHandler`, `OidcLoginSuccessHandler`, `TokenResponseCookieFilter`), the access cookie for 1 h, ticked or not.
- Swing: logout is the File-menu entry "Logout / Rapla neu starten" (`RestartRaplaAction`), which revokes and shows the login dialog again. After an interactive login a consent dialog asks whether to store the token (PRD 126). The top-right menu-bar label shows the username, or an amber "Impersonating <target>" while impersonating (`ApplicationViewSwing`).
- Swing token store (`TokenStores`): JNLP `PersistenceService` first, `~/.rapla/tokens.json` second, one slot for all servers; owner-only permissions on POSIX only, applied after writing. The JNLP key is the codebase URL, identical for every OS user, and the API exposes no file path ([Oracle](https://docs.oracle.com/javase/9/docs/api/javax/jnlp/PersistenceService.html)).
- SPA impersonation swaps the browser-wide `access_token` cookie (`AuthCookieController.impersonateSwitch`/`End`), so all tabs switch together without being notified.
- Impersonation requires `PermissionController.canAdminUser`: global admin, or group admin via `can_admin_parent` ([authentication.md § Group administration policy](../authentication.md#group-administration-policy)), which also grants password resets and user edits. API keys cannot impersonate.
- Category annotations other than the colour cannot be edited in any UI: `CategoryEditUI` maps name, key and colour only; `AnnotationEditCategoryExtension` has no implementation.
- Swing has no unsaved-edit warning on exit: File > Exit closes directly, window close asks a generic exit question.

## Requirements

### Logout and stay-signed-in
- **R1** Swing: the top-right username label becomes a user button with a menu, like the SPA toolbar.
- **R2** `/login` (user decision 2026-10-08, replaces the earlier band/frame variants as too restless): one narrow column (card max ~27rem), all controls the same width with aligned edges, all text left-aligned. One subtle frame (1px light grey, radius 8px) holds the question "Wie lange angemeldet bleiben?", a quiet segmented control ("Nur diese Sitzung" | "Bis zu N Tage"; selected half light blue `#e9f1ff` with blue text and border, no wrap) and one small grey description line for the selected option only; a fine line inside the frame, then small grey uppercase section titles: the SSO provider ("DHBW Keycloak" with one provider, "Single Sign-On" with several) with full-width green buttons, an "oder" divider, "Rapla Server" with username, password and a full-width green "Anmelden" button. Context hint (R19) as a small grey line under the title. Colours: blue only for the selection, green `#27ae60` for buttons. CSS only. Accepted by the user on the dev server 2026-10-08 (client and browser context). **User decision 2026-10-14 (DHBW feedback):** with at least one SSO provider the password fields move one stage back — after the SSO button(s) and "oder" a full-width green "Direkt bei Rapla anmelden" (`login.direct_open`, round 2; first "Direkt am Server anmelden") opens username, password and "Anmelden" as a native `<details>` in the same form (no JS, the remember choice stays); open after a failed login (`?error`); once open, the button disappears and the username gets focus (no collapsing). Round 2 (user accepted `docs/coordination/131-login-feedback/login-mockup.html`, 2026-10-14): grey section titles stay — the provider title (one provider) or "Single Sign-On" over the SSO button(s), "Rapla Server" over the direct button, still shown after it opens. Without SSO the password form stays on the first stage as before, "Rapla Server" title included.
- **R3** Uniform wording in Swing and SPA: German combines "Abmelden" and "Logout" (OQ3), English "Logout".
- **R4** Swing no longer asks whether to store the login; the server returns the stay-signed-in decision with the login.
- **R5** Swing "Abmelden" revokes the session and exits the application.
- **R6** Swing "Beenden (ohne Abmelden)" is enabled while a stored session exists and shown disabled otherwise (user decision 2026-10-07; then quit via "Abmelden (Logout)" or the window close).
- **R7** "Beenden (ohne Abmelden)" sits directly above "Abmelden".
- **R8** Swing "Logout / Rapla neu starten" becomes "Neu anmelden" (revoke, then login dialog).
- **R9** Swing user menu mirrors the SPA menu (switch back, logout with icon).
- **R10** Swing: the user button shows the logout icon visibly on its right (`[👤 name ⇥]`); a click opens the user menu, which holds "Abmelden (Logout)" (user decision 2026-10-07, replaces the separate one-click icon). The SPA keeps logout in the user menu only.
- **R11** A logout in any SPA tab or Swing client signs out every other session of that user (at the next refresh; at the next request once OQ9 is decided).
- **R12** On multi-user machines no other non-admin OS user can read a user's token, also with a system-wide OpenWebStart installation.
- **R13** If storing fails although stay-signed-in was chosen, Swing shows a small ⓘ at the user button; clicking it says the login data could not be stored and names the attempted path. No modal dialog.
- **R18** Swing login waiting dialog ("Warte auf Browser-Anmeldung…"): an inline link "klicken Sie hier" in the text reopens the login page in the browser (no extra button), and the text says that "Abbrechen" leads to the username/password login (user request 2026-10-07).
- **R19** `/login` shows context-specific hints: when it was reached from a Swing login (saved `/oauth2/authorize` request with a loopback `redirect_uri`, `LoopbackUriCheck`), a top hint "Anmeldung für den Rapla Client. Nach der Anmeldung geht es im Client weiter, dieses Browserfenster können Sie dann schließen." and the option texts name the Rapla Client ("Sie bleiben angemeldet, bis Sie den Rapla Client beenden." / "Der Rapla Client speichert die Anmeldung auf diesem Gerät für Ihren Benutzer – auch nach einem Neustart. Mit „Abmelden (Logout)“ endet die Speicherung."); otherwise the browser texts ("Sie bleiben angemeldet, bis Sie den Browser schließen." / "Der Browser speichert die Anmeldung auf diesem Gerät – auch nach einem Neustart. Mit „Abmelden (Logout)“ endet die Speicherung."). Display only; the authorization server keeps validating the redirect (user request 2026-10-08, texts accepted). **User decision 2026-10-14 (DHBW feedback), replaces the client texts above:** hint "Nach erfolgreicher Anmeldung kann dieses Browserfenster geschlossen werden.", options "Sie werden automatisch abgemeldet, wenn Rapla beendet wird." / "Rapla merkt sich die Anmeldung auf diesem Gerät für Ihren Benutzer – auch nach einem Neustart. Mit „Abmelden (Logout)“ endet die Speicherung sofort." (en and the seven other bundles translated).

- **R20** Provider texts on `/login` per language (round 2, 2026-10-14): `rapla.oauth.external.<id>.i18n-bundle` names a deployment ResourceBundle; `login.<id>.button` is the button text, `login.<id>.title` the section title over a single provider. No bundle or no key → `display-name` with the translated "Sign in with" prefix, title = configured display-name without the prefix, else the provider type. Keycloak's default `display-name` is "Sign in with Single Sign-On" (title stays "Keycloak"); the discovery endpoint keeps returning `display-name`. DHBW: `org.rapla.plugin.dhbw.DhbwLoginResources` for `kcdhbw` (dhbwrapla).
- **R21** Deployment styling without a release (round 2, 2026-10-14): `data/patch/login.css` (`rapla.patch-dir`, [PRD 112](done/112-deployment-patch.md) directory) is inlined as a second `<style>` after the built-in one, `data/patch/login.mustache` replaces the built-in template; both are re-read when their modification time changes (one `stat` per request), a template that fails to compile is ignored with a WARN. The built-in style exposes `--accent`, `--accent-hover`, `--select`, `--select-bg` on `:root` and an empty `<div class="logo">` first in `body`. The start-up patch run does not treat `login.mustache` as a stored document. Contract for own templates: the model variables listed in [`docs/deployment.md` § Patch directory](../deployment.md#patch-directory--stored-views-and-documents-from-files-prd-112). DHBW template: dhbwrapla `docs/patch/login.css` (logo as data URI; the mockup's footer links need a template patch, later).

### Impersonation
- **R14** Power users can run several SPA tabs or Swing clients with different identities via switch user; an impersonation started in one tab is not noticed by the other tabs.
- **R15** While impersonating, a tab or client shows no logout (icon or menu) and no "Neu anmelden"; it offers "Zurückwechseln" and "Zu Benutzer wechseln…" (D10); Swing keeps "Beenden". The switch-back label must not say "Admin" (today `shell_switch_back` = "Zurück zum Admin wechseln").
- **R16** An impersonation is not remembered on exit: closing the tab or the Swing client ends it, the next start is the own account. A tab reload keeps it.
- **R17** Members of a group may switch to selected users (e.g. a shared functional account) without being admins. The functional account then needs no own session or shared password, one person's logout affects only that person, and the audit log names the actor.

## Decisions locked

**D1 — One session per user stays.** Logout and "Neu anmelden" sign the user out on every device; "Beenden (ohne Abmelden)" leaves other devices alone. Same model as the documented common expiry ([authentication.md](../authentication.md)). A second device only gets the remaining lifetime, so texts say "bis zu N Tage". `/login?logout` shows "Sie wurden auf allen Geräten abgemeldet." Rejected: per-device sessions.

**D2 — Stay-signed-in is a per-device storage choice, chosen without JavaScript.** The server token is shared (D1); the choice decides whether this device keeps it: SPA persistent vs. session-scoped cookies, Swing file vs. memory only. `/login` is one `<form method="post" action="/login">` with an untitled frame (`<fieldset aria-labelledby>`): the question as a text line, the segmented choice (radios `remember-me` = `off`|`on`, pre-selected from the choice cookie, CSS only), then the SSO entries as `<button formnovalidate formaction="/oauth2/authorization/{id}[?prompt=login]">`, then the password fields (a visually hidden first submit keeps Enter on the password login). POST, because the same form carries the password, which must never reach a URL. `RememberChoiceFilter` (before Spring's OAuth redirect filter) writes `rapla-remember-choice` on POST `/oauth2/authorization/**`; the password handler uses the request parameter (the request still carries the old cookie). The CSP `form-action` includes the IdP origins, because the SSO POST is answered with a redirect to the IdP. `CookieAuthSupport` gets one switch "session-scoped unless remembered", applied to access and refresh cookie at every writer (login handlers, `TokenResponseCookieFilter`, refresh, impersonation end). Reason: the choice cookie is HttpOnly and the `/login` CSP will be enforced (PRD 102), so login-page JS cannot carry it.

**D2a — Browser and Swing label the choice differently (user ruling 2026-10-08).** In both modes the refresh token lives `rapla.auth.remember-me-days` (default 21 d) and there is no idle timeout; the choice only decides whether the cookie (browser) or `tokens.json` (Swing) persists on this device. Browser context: "Anmeldung auf diesem Gerät speichern?" with "Nein" | "Ja, bis zu {0} Tage" (`login.store_*`); "Nein" says "Die Anmeldung gilt nur bis zum nächsten Neustart des Browsers. Stellt Ihr Browser die letzte Sitzung wieder her, bleibt sie erhalten. Mit „Abmelden (Logout)“ endet sie sofort." (`login.remember_session_browser`) — session cookies carry no Max-Age, but browsers that restore sessions keep them (MDN, Cookies › Removal), so "Nur diese Sitzung" was not true there. Swing context keeps "Wie lange angemeldet bleiben?" with "Nur diese Sitzung" | "Bis zu {0} Tage" (`login.remember_*`): the token stays in memory and really ends when the client exits. *Client context superseded by a user decision 2026-10-14 (DHBW feedback): new client texts per R19, password stage behind "Direkt am Server anmelden" per R2; the browser-context labels stay.*

**D3 — `JnlpTokenStore` is removed; one entry per server origin.** Swing stores in `~/.rapla/tokens.json` (`user.home`, under Windows the profile directory), else memory only. Reason: OpenWebStart keeps PersistenceService data in the user profile by default (path unverified, OQ8), so the JNLP store only adds value when `deployment.user.pcachedir` is redirected — exactly where Rapla cannot see or check the location. Entries are keyed by server origin; a token is only ever sent to the origin that issued it; every writer (restore, `MyCustomConnector`, `ClientProxyConfig` refresh) goes through the keyed store. Migration: one re-login and reset login prefs (language, method) for JNLP users; the old pcache token expires within the refresh TTL.

**D4 — Owner-only token file, created that way.** Directory and file are created with owner-only attributes (`PosixFilePermissions.asFileAttribute` / Windows `acl:acl`), an existing own `~/.rapla` is tightened, not refused. Windows: the owner plus the principals that can read the profile directory (SYSTEM, Administrators by default; matched by SID, not localized names) are acceptable; any other principal with read access → memory only plus R13. Two servers on one host and port under different context paths share an entry. The log line names the real path. No app-level encryption (PRD 029 OQ10 stands).

**D5 — No shared OS accounts are assumed.** No server switch to disable persistence.

**D6 — Swing exits.** Three entries: "Neu anmelden", "Beenden (ohne Abmelden)" (plain "Beenden" without a stored session), "Abmelden (Logout)". The PRD 126 consent dialog and its "Nicht mehr fragen" state (`KEY_REMEMBER`) are removed; `ConsentingTokenStore` stays the write gate and receives `setConsent(serverDecision)`. After "Abmelden", Swing persists "next start: prompt=login" in `tokens.json` (today an in-memory flag that an exiting JVM would lose), so the next start does not silently re-authenticate.

**D7 — SPA impersonation is per tab.** The actor's cookie never changes; only the tab that started the impersonation acts as the target (`sessionStorage`). Known copies: "duplicate tab" and browser session restore carry the tab's impersonation along. Rejected: keep the browser-wide cookie swap and let other tabs reload, because other tabs must not notice.

**D8 — Impersonation right as a group annotation, referenced by id.** A group under `user-groups` carries `impersonated_by` (`CategoryAnnotations` constant, OQ11) with comma-separated ids (GraphQL `Group.id`) of the groups whose direct members may switch to its direct members:

```
user-groups
  function-accounts   ← annotation impersonated_by = <id of exporters>
       export
  exporters
       alice, bob
```

`alice` and `bob` may switch to `export`, not to each other; `export` may switch to no one. Ids instead of keys, so renaming a group needs no migration; category ids are persistent (DB `CATEGORY.ID`; XML `id` attribute — a hand-written XML category without `id` gets a fresh id per load until saved). A deleted group grants nothing (fail closed). Changing `impersonated_by`, and membership in any group that carries or is named by an `impersonated_by`, is global-admin-only (same guard family as PH1/E8). Until a category editor exists, the annotation is entered by hand; on a DB deployment with all servers stopped, picked up by the full load at start; no SQL recipe in repo docs.

**D9 — The right sits on a separate group, not below the target.** A `can_admin_parent`-style child group would make its members transitively belong to the target group, giving them the functional account's rights directly and letting them switch to each other.

**D10 — No chains; the actor is always the logged-in user.** Switching while impersonating (A → B → C) is allowed and checked as A → C with A's rights; the picker lists against A. Clients always switch with the actor's own credential (SPA: the unchanged cookie; Swing: the actor's token, today refused client-side), and the server rejects every impersonation request made with a token carrying an `act` claim. Not-permitted and unknown targets get the identical response (403, empty body).

**D11 — Switch only.** `canImpersonate(actor, target)` = `canAdminUser` or the D8 annotation match; via D8 never self and never an admin or group-admin target (the `canAdminUser` path is unchanged, including an admin's self-switch), and the kill switch `rapla.auth.impersonation.enabled` covers both. It grants no admin operation; password reset, user edit etc. stay on `canAdminUser`.

**D12 — Act-as: mandatory on cookie requests, never on token requests.** One filter right after token validation turns `X-Rapla-Act-As` into the shape of today's impersonation token (`sub` = target, `act` = actor), so nothing downstream reads the header. Every cookie-authenticated request under `/api` carries the header (`self` or a user id), set by one `HttpClient` interceptor; a missing header is rejected with 400, never run as the actor. Exceptions: `GET /api/documents/**` takes `actAs` as query parameter and defaults to `self` (bookmarks, shared links); the public `GET /api/graphql/schema` and `GET /api/v3/api-docs/**` need no header (plain navigations from GraphiQL/Swagger). Misuse (header on Bearer, id with an `act` token or API key, misplaced parameter) → 400; kill switch, unknown and not-permitted → 403. A switch returns `ImpersonationTarget(id, username)`; `impersonate/end` no longer re-mints a cookie and is no longer called by the SPA. Bearer requests (API tokens, Swing) need no header, and act-as on a Bearer or `act` request is rejected: API tokens never impersonate; external API users use API tokens. Audit happens at the explicit switch call. Per-request act-as is audited once per actor token and target (`ActAsFilter`, logger `rapla`). ESLint bans `fetch`, `XMLHttpRequest`, `EventSource`, `WebSocket`, `navigator.sendBeacon` and `window.open`/`location` on `/api` outside one helper that appends `actAs` (with `noopener`). The static pages (GraphiQL, template editor, Swagger UI) add `self` next to their hand-set XSRF header. Behaviour inventory of the replaced cookie swap (`impersonateSwitch`/`End`): authorization + audit stay at the switch call, the full reload (R-22) and the unsaved-draft confirm stay, A → B → C stays (D10).

**D13 — `rapla-remember-me` is removed.** Under D2 its effect was a silent re-authentication of session-less requests on the second filter chain (the explorer pages GraphiQL/Swagger) after a browser restart, with a sliding lifetime past the refresh cap; `/oauth2/authorize` was never authenticated by it. Behaviour inventory: SPA keeps its session through the refresh cookie (D2), Swing through its stored token (D3); an SSO user re-authenticates silently at the IdP as before; the explorer pages need a valid `access_token` cookie or a fresh login. The inert `org.rapla.auth.rememberMeTokens` system-preference entry stays in existing data. `rapla.auth.remember-me-days` stays the one lifetime N (default 21 d, OQ1) and now drives the refresh TTL only.

**D14 — The /login page is a JMustache template.** Markup, CSS and script live in one classpath template; `LoginPageController` only builds the model and renders with the JMustache already used for documents (PRD 097). Default `{{…}}` escaping replaces hand-written `HtmlUtils` calls; a test renders preview files for both contexts so the page can be reviewed without a server (user decision 2026-10-08).

## Documented behaviour (no change planned)

- Residue (not this PRD): the Swing login dialog still shows the "RAPLA 2" logo.

- Two Swing clients on one OS account logged in as *different real* users share the per-server `tokens.json` slot; the last writer wins at the next start. Switch user is unaffected because its token stays in memory.
- Logout in a non-impersonating tab or client signs out impersonating tabs/clients too, since impersonation rides on the actor's session.
- Free links written into a document template do not carry `actAs` and open as the actor.

## Residue (review round 22, LOW — after M3; L1–L3 before production)

- **L1** `FileTokenStore` writes are not synchronized and not atomic → write to an owner-only temp file + `ATOMIC_MOVE`.
- **L2** `FileTokenStore` does not switch to the memory fallback on a write IO error.
- **L3** `OwnerOnlyFiles` does not tighten an existing 0644 `tokens.json` → tighten, or state it in D4.
- **L4** `SessionMenu.storedSession()`: "Beenden (ohne Abmelden)" is enabled for another user's entry or a memory-only token, and reads the file on the EDT at every menu open.
- **L5** ESLint: D12 names a ban on `location` with `/api`, but no rule exists; the act-as interceptor only matches relative `/api/` URLs.
- **L6** `ActAsFilter` builds the Jwt without `iss`/`aud` → say so in the class comment.
- **L7** `GET /api/users` ignores the impersonation kill switch (cosmetic).
- **L8** CSP `form-action` lists the IdP origins on all SPA/login pages (report-only; acceptable).
- **L9** Transition: an impersonation cookie minted before the deploy keeps its target for up to 1 h.
- **L10** Pre-existing: `RaplaClientServiceImpl` (~l. 635-637) deletes the stored login on any non-success refresh including 5xx; `MyCustomConnector` (~l. 133/136) puts response bodies into exception messages.
- **22c** `ActAsFilter` audit dedupe keys by `jti`; a token without `jti` would key `null|<target>` and log only once per process → log every time when `getId()` is null (all current writers set `jti`). Before production.
- **22b** Navigating to `/login` by hand leaves the tab's `rapla.actAs` → optional inline removal in `login.mustache`.

## Plan

### A — Swing token store
- [x] Per-origin entries (`refreshToken@<origin>`, origin = scheme + host + port; the store reads the server URL at every call), owner-only creation, tighten own directory, memory fallback with `TokenStore.failure()` for R13, real path in the log (D3, D4).
- [x] Remove `JnlpTokenStore`; `TokenStores` = file, else memory.

### B — Server impersonation rules
- [x] `PermissionController.canImpersonate` + `CategoryAnnotations.IMPERSONATED_BY` (D8, D11).
- [x] Global-admin-only guard in `SecurityManager` for `impersonated_by` and membership in carrying/named groups (D8); deleting such a group only revokes the right (fail closed).
- [x] Both impersonation endpoints: `canImpersonate`, Bearer endpoint rejects `act` tokens, unknown ≡ not-permitted (403, empty body) (D10); `GET /api/users` lists the `canImpersonate` targets of the real actor; shared act/real-actor logic in `ImpersonationActor`.

### C — Lifetime and remember-me
- [x] Remove `rapla-remember-me` (D13, `RaplaTokenRepository` deleted); `rapla.auth.remember-me-days` (default 21 d) drives refresh JWT, refresh cookies and the SAS refresh-token expiry via `RefreshSessionService.refreshTokenTtlSeconds()` (OQ1).
- [x] `/login?logout` shows the all-devices hint (`login.logged_out_all_devices`, D1).

### D — Per-tab impersonation
- [x] Act-as filter + 400 rule + document query parameter + Bearer rejection (D12): `ActAsFilter` right after the bearer filter (`CookieToBearerFilter` marks cookie requests); a permitted id replaces the authentication by a hand-built `Jwt` of the impersonation shape (no signing, actor's `exp`). `GET /api/graphql/schema` and `GET /api/v3/api-docs/**` stay open to plain links (public documents, GraphiQL's SDL link). `DocumentRenderService.gateParams` ignores `actAs`. Cookie swap removed: `impersonateSwitch` authorizes, audits and returns `{id, username}`; `impersonateEnd` only checks that the tab impersonates.
- [x] SPA: `sessionStorage` key `rapla.actAs`, `actAsInterceptor`, `openDocument()`, ESLint bans (`no-restricted-globals` fetch/XMLHttpRequest/EventSource/WebSocket, `no-restricted-properties` sendBeacon/window.open/window.fetch; `auth/open-document.ts` exempt); switch keeps authorization, audit, reload and draft confirm, end is client-only (D7, D12).
- [x] Static pages send `self`.

### E — Swing UI
- [x] `UserBar` (one bordered button `[👤 name ⇥]` with the logout icon inside on the right, a click opens the popup; no separate logout button; logout icon `logout.png`, ⓘ = `icon.info_small` with non-modal text incl. path), amber "Als {0} angemeldet" while impersonating; menu decisions in `SessionMenuModel`, shared by popup and File menu via `SessionMenu` (R1, R9, R10, R13, R15). Smoke test on a display pending.
- [x] "Neu anmelden" / "Beenden (ohne Abmelden)" / "Abmelden (Logout)" in user menu and File menu (R5–R8, OQ4), no confirmation (OQ5); `logoutAndExit()` = revoke, clear own entry, persist prompt, `NextSession.exit()`; while impersonating only plain "Beenden"; `RestartRaplaAction` deleted.
- [x] Session-ended dialog: "Sie wurden abgemeldet." with "Neu anmelden" / "Beenden" (window close = Neu anmelden).
- [x] `SwitchUserPanel` for anyone with a non-empty `/api/users` list, switching by username (R17); chained switch allowed, `RefreshOn401Interceptor` sends the actor token on `/api/auth/impersonate`; renewal already used it (D10).
- [x] Login waiting dialog: inline link "klicken Sie hier" in `login.oauth.waiting` (`JEditorPane` + `HyperlinkListener`, `LoginDialog.setReopenLink`, `SwingOAuthLoginFlow.Session.authorizeUrl()`); text names "Abbrechen" → username/password login; text no longer clipped (R18). Visual check pending.
- [x] OQ10 rule: `setConsent(false)` keeps the stored entry; logout and session end clear it only if its JWT `sub` matches (unreadable → clear).

### F — Login page and server decision
- [x] /login as JMustache template `rapla-app/src/main/resources/templates/login.mustache` (escaper `HtmlUtils::htmlEscape` keeps the old entities); 11 variants byte-identical after whitespace normalisation; `LoginPagePreviewTest` writes `rapla-app/target/login-preview-{browser,client}.html` (D14).
- [x] No-JS form with checkbox above the SSO buttons (segmented choice `login.remember_session` / `login.remember_days`, question `login.remember_question`; `remembered = "on".equals(param)`), `RememberChoiceFilter`, form handler reads the parameter (R2, D2).
- [x] `CookieAuthSupport.set{Access,Refresh}TokenCookie(…, remembered)` at all writers: login handlers, `TokenResponseCookieFilter`, refresh, impersonation switch/end (D2).
- [x] `RememberAwareAuthorizationResponseHandler` appends `remember=1|0` to loopback redirects; `SwingOAuthLoginFlow` → `ConsentingTokenStore.setConsent` (legacy password login → false, OQ7); consent dialog and "Nicht mehr fragen" removed; `logout()` persists `nextLoginPrompt=login`, consumed at start, cleared after a successful login (R4, D6).

### G — SPA toolbar and labels (spots released by the owning session 2026-10-08)
- [x] While impersonating: "Zu Benutzer wechseln…" stays, no logout entry, switch back relabelled (`shell_switch_back` = "Zurückwechseln" / "Switch back"); `shell_sign_out` = "Abmelden (Logout)" / "Logout" (R3, R10, R15); spec case added.

### H — Immediate logout (OQ9, deferred)
- [ ] Session check for access tokens — **deferred** (OQ9, user 2026-10-08: decide later); not part of the first test deploy. Until then a logout elsewhere takes effect at the next refresh.

### I — Docs
- [x] [authentication.md](../authentication.md): logout semantics, stay-signed-in, remember-me removal, Swing token store, per-tab impersonation (rewrite §§ cookie switch / A→B→C), impersonation right and setup, documented behaviour above.

### Later — not part of this PRD
- Dev mode with live reload of `login.mustache` and i18n properties (no restart) — handed to the coordinator session as its own PRD task (2026-10-08). Since R21 a `login.mustache` / `login.css` in the patch directory reloads without restart in every mode; the built-in template and the properties bundles still need one.
- Category annotations over GraphQL ([PRD 117](117-graphql-administration-entities.md) E11) and an SPA-only category editor with a group picker for `impersonated_by` (D8).

## Tests

- Tier 1: `FileTokenStore` — per-origin isolation (a token is never returned for another origin); owner-only creation; tightening of an existing own directory; refusal of a store readable by another principal → memory; Windows ACL (`@EnabledOnOs(WINDOWS)`).
- Tier 1: `canImpersonate` — direct member allowed; non-member, transitive member, admin target, group-admin target, self refused; renamed group still matches; deleted group grants nothing; kill switch.
- Tier 2: `SecurityManager` dispatch — group admin cannot set `impersonated_by` or change membership of a named group (style of `SecurityManagerDispatchFieldGuardTest`).
- Tier 3: impersonation endpoints — `act` token refused; not-permitted ≡ unknown target (§12; replaces `unknownTargetReturns404`); picker lists against the actor.
- Tier 3: act-as filter — cookie without header → 400, `self` → actor, permitted id → target, not permitted ≡ unknown, act-as on Bearer/API token/`act` token → rejected, query parameter outside document GET → rejected, document GET without `actAs` → actor; audit line at the switch call.
- Tier 3: checkbox → persistent vs. session-scoped access and refresh cookies for password and OIDC; `/oauth2/authorize` redirect carries the decision; no `rapla-remember-me` cookie is issued.
- Tier 5: interceptor sets `self` / the id; document helper appends `actAs`. Tier 7: two tabs, impersonate in one, the other stays the actor.
- Review round 22 (2026-10-08): M1 fixed — the SPA drops `rapla.actAs` on `signOut()` and `redirectToLogin()` (also the 401 bounce), spec cases red→green, `ng test` 869/869. M2 fixed — the stay-signed-in decision travels across Swing context rebuilds (`ConnectInfo.remember`, `applyCarriedDecision` before the first write); "Nur diese Sitzung" + switch user/back no longer persists; revert check reproduced M2; `mvn -pl rapla-client -am test` green (client 142, 0 failures). M3 fixed — act-as audit line once per actor token and target; LOWs L1–L10 follow.
- Full lanes 2026-10-08 01:21 (all packages incl. G, login.mustache): `mvn test` 2492 run, 0 failures, 0 errors, 4 skipped; `ng test` 867/867.
- Manual smoke 2026-10-07 (dev server on a DHBW data copy, Swing via `exec:java`) — passed: `/login` checkbox above the SSO buttons; no consent dialog; "Abmelden" revokes, clears the entry and exits, the next start forces `prompt=login`; with stay-signed-in, "Beenden (ohne Abmelden)" + restart signs in silently; `tokens.json` holds `refreshToken@<origin>` with 0600/0700; switch user shows the amber cue without logout icon; switch back works. Open: ⓘ, session-ended dialog, webstart, SPA cookie checks; the log line "stay signed in: true (… stored false)" appears although the token is stored (check).

## Open Questions

- **OQ1** — N = 21 d or 30 d? *Resolution:* one config value, Rapla default 21 d — keeps the [PRD 072](done/072-server-side-login-dialog.md) #7 cap; deployments may set their own.
- **OQ2** — Mechanism for D7. *Resolution:* target id in `sessionStorage`, sent as `X-Rapla-Act-As`, turned into an impersonation-shaped token by one filter (D12). This restores the per-tab behaviour [PRD 051](done/051-switch-user-with-oauth.md) designed (`sessionStorage`, lost with the [PRD 072](done/072-server-side-login-dialog.md) cookie swap) and answers 051's main objection to a per-request header (every reader of the security context would need to know the override). `sessionStorage` survives reloads and ends with the tab ([MDN](https://developer.mozilla.org/en-US/docs/Web/API/Window/sessionStorage)). Opened documents are server-rendered without own API requests; their window navigation copies all request parameters (`DocumentRenderService.navUrl`), so `actAs` travels along. Rejected: impersonation token in `sessionStorage` (conflicts with PRD 102).
- **OQ3** — German label "Abmelden (Logout)" or "Abmelden / Logout"; English for "Neu anmelden". *Proposal (implemented, i18n only):* "Abmelden (Logout)".
- **OQ4** — Exit entries in the user menu, the File menu, or both? *Proposal (implemented):* both.
- **OQ5** — Swing logout icon without confirmation? *Proposal (implemented):* no confirmation; Swing has no unsaved-edit warning either.
- **OQ6** — Swing switch-user dialog here or later? *Resolution:* here, package E.
- **OQ7** — Legacy Swing password dialog gets its own stay-signed-in checkbox? *Resolution:* no; legacy-mode password users keep the dialog ([PRD 126](done/126-swing-sso-auto-login.md) D1, 2026-10-03).
- **OQ8** — Verify `user.home` and the OpenWebStart pcache default under Windows on a test machine.
- **OQ9** — Immediate logout: per-request session check (proposal from review: a `sid` claim in every rapla access token, compared by equality; `act` tokens checked against the actor; API-key and external tokens exempt) vs. a shorter access TTL. *Resolution:* decided later (package H).
- **OQ10** — Several real logins without impersonation. *Resolution:* impersonation right for groups (R17, D8–D11); a second browser profile stays possible. Regardless, a login without stay-signed-in never touches the stored entry, and logout clears it only if it belongs to that user.
- **OQ11** — Annotation key name. *Proposal (implemented as one constant):* `impersonated_by`.
