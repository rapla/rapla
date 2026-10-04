# PRD 126 — Swing: silent reauth + SSO auto-start in the legacy login dialog

**Status:** implemented, uncommitted — 2026-10-03 (D1–D5 as below; local `-Psign-jks` build for a localhost login test; OQ1–OQ3 ruled, dhbw-test check open)
**Related:** [PRD 029](029-swing-oauth-login.md) (Swing OAuth, Phase 3 legacy dialog), [PRD 072](done/072-server-side-login-dialog.md) (rapla brokers Keycloak; `prompt=login` rules), [PRD 031](done/031-token-refresh-and-api-keys.md) (refresh-token model), [docs/authentication.md § Swing client](../authentication.md#swing-client--default-oauth-fallback-password-dialog)

## Abstract

DHBW runs the Swing client with `rapla.oauth.swing-legacy-login=true` and
`swing-legacy-show-sso-button=true` (`~/git/dhbwrapla/docs/application-web.yml:40–41`,
`application-test.yml:43–44`). In that mode every start shows the password dialog, SSO is
only preselected, and the user has to click Login and sign in at Keycloak again
(the Keycloak link always carries `prompt=login`, `LoginPageController.java:336`). Wanted:
if the last successful login was SSO, the next start logs in silently from the stored
refresh token; if that fails, the browser SSO flow starts by itself. The dialog's Abort
still returns to the password form.

## Findings (2026-10-03)

- The refresh token **is already persisted** after every Swing login, SSO and password
  alike: `RaplaClientServiceImpl.login(ConnectInfo)` →
  `tokenStore.tryWrite(...)` (`RaplaClientServiceImpl.java:1395–1398`); the 401
  interceptor stores rotated tokens (`ClientProxyConfig.java:329`,
  `MyCustomConnector.java:145`). Store backends: JNLP `PersistenceService` →
  `~/.rapla/tokens.json` → no-op (`TokenStores.java`).
- The last method is persisted as `TokenStore.KEY_LOGIN_METHOD` = `"sso"` /
  `"password"` (`finishOauthLogin` → `persistLoginPrefs("sso")`,
  `RaplaClientServiceImpl.java:1154`) and only used to preselect the method combo
  (`configureLegacyDialogMethods`, lines 1024–1032).
- The gap is one branch: `startLogin()` skips the silent reauth whenever
  `swingLegacyLogin` is true (`RaplaClientServiceImpl.java:560–569`), and
  `startLoginInThread()` auto-fires `runOauthLogin` only when
  `oauthEnabled && !legacyLogin` (lines 918–926). Everything else (silent reauth
  `tryRestoreFromCachedRefreshToken`, the waiting-mode dialog, Abort → full dialog via
  the `CancellationException` branch at lines 1101–1108, `prompt=login` after logout via
  `LogoutSignal`) exists.
- Server side needs no change: the rapla refresh token lives 30 d and is renewed when
  used within 7 d of expiry (oauth-flow skill); the stored token is shared across devices
  of one user, so an SPA login does not invalidate Swing's token
  (`RefreshSessionService.java:35–40`). Silent reauth never opens the browser, so
  Keycloak's always-on `prompt=login` is not hit on routine starts.
- `/oauth2/revoke` on logout clears the server entry for **all** devices of the user
  (`RefreshSessionService.java:266–274`) — an SPA logout kills Swing's silent reauth and
  vice versa; the fallback is then the browser flow.

## Decisions

- **D1 — Silent reauth in legacy mode only for SSO users.** `startLogin()` tries
  `tryRestoreFromCachedRefreshToken` when OAuth is enabled and (`!swingLegacyLogin` or
  `KEY_LOGIN_METHOD == "sso"`). Password users keep today's dialog (they are the rollout
  population the legacy flag exists for; OQ1).
- **D2 — SSO auto-start in the legacy dialog.** When silent reauth did not restore and
  the saved method is `sso` and the SSO method is offered
  (`swingLegacyShowSsoButton`), `startLoginInThread()` configures the legacy methods as
  today (so Abort shows the password form with SSO preselected) and then calls
  `runOauthLogin` immediately, same as the non-legacy default. Abort cancels the
  browser wait and leaves the full dialog (existing branch). SSO button hidden by the
  admin ⇒ no auto-start, pref ignored.
- **D3 — No auto-start after an explicit logout** (user ruling 2026-10-03, OQ3): the
  legacy password dialog is shown, SSO stays preselected; choosing it sends
  `prompt=login` from `LogoutSignal` as before. Detected via the non-consuming
  `LogoutSignal.isForceOauthLoginNext()`. The non-legacy mode keeps its auto-start.
- **D4 — No server change, no config knob.** The behaviour follows the stored
  preference; no new property.
- **D5 — Decision extracted into a pure function**
  `RaplaClientServiceImpl.autoSso(cfg, savedMethod, afterLogout)` (boolean: skip the dialog —
  silent reauth, then browser SSO — or not), tier-1 tested in
  `RaplaClientServiceImplAutoSsoTest`; `startLogin()` and `startLoginInThread()` both
  read it. The planned tri-state was not needed: silent reauth always precedes the
  browser flow.

## Plan

1. ✅ Tier-1 test `RaplaClientServiceImplAutoSsoTest` (6 cases: after-logout, legacy+sso+button,
   legacy+password/none, button hidden, non-legacy ignores pref, disabled/no discovery).
2. ✅ `startLogin()`: `silentReauthAllowed = autoSso(cfg, savedMethod)`.
3. ✅ `startLoginInThread()`: auto-fire when `autoSso`, after
   `configureLegacyDialogMethods` in legacy mode (Abort → password form).
4. ✅ docs/authentication.md § Swing client item 3.
5. ⏳ Manual check on dhbw-test with a Windows OpenWebStart client: log line
   `token store: JNLP PersistenceService` or `file`, then start → no dialog; delete the
   store → browser opens by itself; Abort → password form.

Estimate: half a day incl. the dhbw-test check.

## D11 — DHBW runs the default mode (User ruling 2026-10-04)

DHBW switches to `swing-legacy-login: false` (`dhbwrapla/docs/application-web.yml`, `application-test.yml`,
`local/application.yml`): every start opens the SSO page at once, the Swing dialog stays visible in waiting
mode with Abort, Abort shows the password form with the method combo (password / SSO) so SSO can be retried
without a restart. For that the auto-start path now configures the method combo in both modes
(`configureLegacyDialogMethods` before `runOauthLogin`). Silent reauth from the stored refresh token runs
before the browser on every start. D1–D3 remain for installations that keep the legacy dialog. Assumption
stated to the user: in this mode an explicit logout auto-starts the browser with `prompt=login` (parity with
the default), not the password dialog of D3.

## Phase 2 — consent for storing the refresh token (User ruling 2026-10-04, implemented)

- After every interactive login (password or SSO), once per machine, a modal question from the login
  dialog: "Anmeldung auf diesem Rechner speichern bis <Datum> oder bis zur Abmeldung?" with Ja (default) /
  Nein and "Nicht mehr fragen". The date is the `exp` claim of the refresh token
  (`ConsentingTokenStore.expiryOf`); without it the text says "bis zur Abmeldung". Silent reauth never asks.
- Yes and stored → continues at once, no confirmation. Yes but the backend cannot store (no OpenWebStart
  `PersistenceService`, no writable profile) → one information box, then continues. No → any token on disk
  is removed and later rotations are not written.
- Mechanics: `TokenStores.create()` wraps the backend in `ConsentingTokenStore` (rapla-core). Until the
  decision the latest token is held in memory (`login()` and the 401 interceptor keep calling `tryWrite`);
  `setConsent(true)` writes it and verifies by reading back. The standing answer is the preference
  `remember` = yes/no (`TokenStore.KEY_REMEMBER`), written only with "Nicht mehr fragen". A token already on
  disk from before this feature counts as consent and is persisted as `remember=yes` (so a later rejected token
  does not ask again; only a logout does). The question runs on the EDT with the login dialog in its "load"
  state behind it. An explicit logout calls `forgetDecision()` (ask again);
  the auth-dead path does not.
- No admin switch (decided: add a discovery flag only when a site asks for it).
- Texts in `RaplaResources` (en) and `_de`; the other languages fall back to English until the PRD 103 pass.
- Tests: `ConsentingTokenStoreTest` (7, tier 1). The dialog itself is Swing wiring in
  `RaplaClientServiceImpl.askRememberLogin`, exercised manually on the copy.

## Found during the local test (2026-10-04)

Login dialog and stay-signed-in question in English although rapla runs in German and the browser login
page is German: with "Default Preferences" the server system locale was applied only after login, the
dialog used the JVM locale (`C.UTF-8` here). Now the discovery payload `/api/auth/oauth/config` carries
`language` (system preference `org.rapla.locale`, the same source `LoginPageController` uses) and the
Swing dialog switches to it before rendering the waiting text when no explicit language was chosen; the
chooser stays on "default" so `Application.initLanguage` writes no user preference. Test
`OAuthConfigControllerTest.discoveryCarriesTheServerLanguage` (sets `fr_FR`, expects `fr`). Discovery was
already the first call, so the same probe tells the client whether the server is reachable at all.

The chooser entry "Default Preferences" is called "User setting" / "Benutzereinstellung" in the login dialog
(`login.language.user_setting`, en+de; user and start options keep the old label). The stay-signed-in
question moved from both login paths into `beginRaplaSession()` after `facade.load()` and the locale setup, so
it uses the language of the user's preferences; parent is the still-open login dialog (`activeLoginDialog`),
shown on the EDT via `invokeAndWait` so the session start waits. Without an open login dialog (silent reauth,
impersonation) nothing is asked. A remembered session language (`uiLanguage`) was dropped (User 2026-10-04).


Exit while the browser wait runs: the scheduler shutdown interrupts the worker in `runOauthLogin`, and
the error branch showed the `InterruptedException` as an error dialog. `isLoginCancelled` now treats
`InterruptedException` like `CancellationException` (no dialog); `RaplaClientServiceImplAutoSsoTest` 7/7. In
addition the Exit button is disabled while the browser wait runs (User: "why not disable exit"); Abort
re-enables it with the form.

## Found during the local test (2026-10-03)

A refused provisioning (dhbwrapla Standort check, audit S5) was logged and the
Swing authorize resumed with the external principal; the token exchange then failed
with "Cannot resolve user for refresh-token issuance: <login>" and the reason was
lost. Fixed independently of the Standort rule: `OidcLoginSuccessHandler.abortLogin`
(session ended, loopback error redirect with `error_description`, or `/login?error`
with the reason stored once in the session), `SwingOAuthLoginFlow` shows
`error_description`, `LoginPageController` renders the stored reason. Tests:
`OidcLoginSuccessHandlerTest` (2 new), `LoginPageHintTest`, `SwingOAuthLoginFlowTest`.

## Open Questions

All answered by the user 2026-10-03: OQ1 no (password users keep the dialog, D1);
OQ2 verify on dhbw-test (plan step 5, open); OQ3 password dialog after logout (D3).
