package org.rapla.client.spring;

import org.rapla.ConnectInfo;

/**
 * Carrier between the bean inside the {@code AnnotationConfigApplicationContext}
 * and the launcher loop in {@link SpringRaplaClient#main(String[])}. Each
 * iteration of {@code main()}'s loop closes the current context and builds a
 * new one with the {@link #info()} contained here.
 *
 * <p>Four intents:
 * <ul>
 *   <li>{@link #showLoginDialog()} — next session has no auto-login; the user
 *       sees the login dialog. Used by {@code logout()}.</li>
 *   <li>{@link #reconnectAs(ConnectInfo)} — next session auto-connects with
 *       the supplied tokens. Used by the initial JVM launch when args carry a
 *       bootstrap token.</li>
 *   <li>{@link #switchTo(ConnectInfo, String, String)} — admin → user
 *       impersonation. The next context starts with admin's full session as
 *       primary AND has the impersonation override set so outbound calls use
 *       the impersonation token while renewal calls authenticate as admin.</li>
 *   <li>{@link #switchBack()} — return to the previously-saved admin session.
 *       The launcher uses its remembered {@code savedAdminInfo} (admin's full
 *       4-tuple) rather than {@link #info()}.</li>
 *   <li>{@link #exit()} — break out of {@code main()}'s loop. Used by the
 *       "Exit Rapla" menu action.</li>
 * </ul>
 *
 * <p>Per PRD 029 Phase 5, {@link ConnectInfo} carries the full session
 * (tokens + provider routing) and impersonation is a separate sidecar — the
 * launcher applies it after {@code clientService.start(info)} via
 * {@code ClientService.setImpersonation(...)}. This mirrors the Angular SPA's
 * two-slot model — admin tokens in the OAuth library, impersonation in
 * {@code AuthService.impersonationOverride}.
 */
public final class NextSession
{
    private final ConnectInfo info;
    /** Optional: admin's own full session info to remember for a later
     *  switch-back. Set by {@link #switchTo(ConnectInfo, String, String)}. */
    private final ConnectInfo restoreInfo;
    /** Optional: impersonation access token (rapla-SAS-signed; no refresh). Set
     *  by {@link #switchTo(ConnectInfo, String, String)}. The launcher applies
     *  this via {@code ClientService.setImpersonation(...)} after starting the
     *  new context with {@link #info()}. */
    private final String impersonationAccessToken;
    /** Optional: impersonation target username. Set whenever
     *  {@link #impersonationAccessToken} is set. */
    private final String impersonationTargetUsername;
    private final boolean exit;
    private final boolean switchBack;
    /** True only after an explicit logout: the next context's first OAuth flow
     *  must send {@code prompt=login} so the server terminates the browser's
     *  still-live session instead of silently re-authenticating the old user.
     *  Must travel here — a flag on a context-internal bean dies with the
     *  context this signal tears down. */
    private final boolean forceOauthLogin;

    private NextSession(ConnectInfo info, ConnectInfo restoreInfo,
                        String impersonationAccessToken, String impersonationTargetUsername,
                        boolean exit, boolean switchBack, boolean forceOauthLogin)
    {
        this.info = info;
        this.restoreInfo = restoreInfo;
        this.impersonationAccessToken = impersonationAccessToken;
        this.impersonationTargetUsername = impersonationTargetUsername;
        this.exit = exit;
        this.switchBack = switchBack;
        this.forceOauthLogin = forceOauthLogin;
    }

    public ConnectInfo info() { return info; }
    public ConnectInfo restoreInfo() { return restoreInfo; }
    /** Non-null only on {@link #switchTo(ConnectInfo, String, String)}. */
    public String impersonationAccessToken() { return impersonationAccessToken; }
    /** Non-null only on {@link #switchTo(ConnectInfo, String, String)}. */
    public String impersonationTargetUsername() { return impersonationTargetUsername; }
    public boolean isExit() { return exit; }
    public boolean isSwitchBack() { return switchBack; }
    public boolean isForceOauthLogin() { return forceOauthLogin; }

    public static NextSession showLoginDialog() { return new NextSession(null, null, null, null, false, false, false); }
    /** Like {@link #showLoginDialog()} but marks the session as following an
     *  explicit logout — see {@link #isForceOauthLogin()}. */
    public static NextSession showLoginDialogAfterLogout() { return new NextSession(null, null, null, null, false, false, true); }
    public static NextSession exit() { return new NextSession(null, null, null, null, true, false, false); }
    public static NextSession reconnectAs(ConnectInfo info) { return new NextSession(info, null, null, null, false, false, false); }
    /**
     * Admin → user impersonation: launcher should use {@code adminFullInfo}
     * for the next context (admin's tokens as primary) AND apply the
     * impersonation override on top.
     *
     * @param adminFullInfo admin's session info (access + refresh tokens) —
     *   restored as the primary session on the new context AND remembered for
     *   the eventual switch-back (PRD 072 Phase 5: refresh always hits rapla,
     *   so no provider routing to carry)
     * @param impersonationAccessToken the rapla-SAS-signed impersonation JWT
     *   returned from {@code POST /api/auth/impersonate}
     * @param impersonationTargetUsername the impersonated user's username
     *   (for UI indicators + impersonation-renewal calls)
     */
    public static NextSession switchTo(ConnectInfo adminFullInfo,
                                       String impersonationAccessToken,
                                       String impersonationTargetUsername)
    {
        return new NextSession(adminFullInfo, adminFullInfo,
                impersonationAccessToken, impersonationTargetUsername, false, false, false);
    }
    /** Return to the previously-saved admin session. The launcher uses
     *  its remembered {@code adminFullInfo}, not {@link #info()}. */
    public static NextSession switchBack() { return new NextSession(null, null, null, null, false, true, false); }
}
