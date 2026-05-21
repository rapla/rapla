package org.rapla.client.spring;

import org.rapla.ConnectInfo;

/**
 * Carrier between the bean inside the {@code AnnotationConfigApplicationContext}
 * and the launcher loop in {@link SpringRaplaClient#main(String[])}. Each
 * iteration of {@code main()}'s loop closes the current context and builds a
 * new one with the {@link #info()} contained here.
 *
 * <p>Three intents:
 * <ul>
 *   <li>{@link #showLoginDialog()} — next session has no auto-login; the user
 *       sees the login dialog. Used by {@code logout()}.</li>
 *   <li>{@link #reconnectAs(ConnectInfo)} — next session auto-connects with
 *       the supplied tokens / credentials. Used by switch-to-user
 *       (PRD 051), switch-back, and the initial JVM launch when args
 *       carry credentials.</li>
 *   <li>{@link #exit()} — break out of {@code main()}'s loop. Used by the
 *       "Exit Rapla" menu action.</li>
 * </ul>
 */
public final class NextSession
{
    private final ConnectInfo info;
    /** Optional: admin's own {@link ConnectInfo} to remember for a later
     *  switch-back. Only set by {@link #switchTo(ConnectInfo, ConnectInfo)}. */
    private final ConnectInfo restoreInfo;
    private final boolean exit;
    private final boolean switchBack;

    private NextSession(ConnectInfo info, ConnectInfo restoreInfo, boolean exit, boolean switchBack)
    {
        this.info = info;
        this.restoreInfo = restoreInfo;
        this.exit = exit;
        this.switchBack = switchBack;
    }

    public ConnectInfo info() { return info; }
    public ConnectInfo restoreInfo() { return restoreInfo; }
    public boolean isExit() { return exit; }
    /** True when the launcher should use the previously-saved admin
     *  {@link ConnectInfo} rather than {@link #info()} — used to return
     *  from an impersonation session to the admin's own session. */
    public boolean isSwitchBack() { return switchBack; }

    public static NextSession showLoginDialog() { return new NextSession(null, null, false, false); }
    public static NextSession exit() { return new NextSession(null, null, true, false); }
    public static NextSession reconnectAs(ConnectInfo info) { return new NextSession(info, null, false, false); }
    /**
     * Admin → user impersonation: launcher should use {@code impersonationInfo}
     * for the next context, AND remember {@code adminRestoreInfo} so a later
     * {@link #switchBack()} can restore the admin session. {@code adminRestoreInfo}
     * is the admin's current credentials captured before the context closes
     * (because connectionInfo dies with the context).
     */
    public static NextSession switchTo(ConnectInfo impersonationInfo, ConnectInfo adminRestoreInfo)
    {
        return new NextSession(impersonationInfo, adminRestoreInfo, false, false);
    }
    /** Return to the previously-saved admin session. The launcher uses
     *  its remembered {@code adminInfo}, not {@link #info()}. */
    public static NextSession switchBack() { return new NextSession(null, null, false, true); }
}
