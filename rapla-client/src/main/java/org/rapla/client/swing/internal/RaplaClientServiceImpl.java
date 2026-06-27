/*--------------------------------------------------------------------------*
main.raplaContainer.dispose();
             | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.swing.internal;

import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.client.Application;
import org.rapla.client.api.ClientService;
import org.rapla.client.api.RaplaClientListener;
import org.rapla.client.UserClientService;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.LanguageChooser;
import org.rapla.client.internal.LoginDialog;
import org.rapla.client.internal.OAuthConfig;
import org.rapla.client.internal.OAuthTokens;
import org.rapla.client.internal.SwingOAuthLoginFlow;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingSafe;
import org.rapla.client.swing.SwingSchedulerImpl;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.i18n.SwingBundleManager;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.User;
import org.rapla.facade.UpdateErrorListener;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.ClientFacadeImpl;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.TokenStore;
import org.rapla.storage.dbrm.OAuth2PasswordLogin;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteOperator;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.Semaphore;

/** Implementation of the UserClientService.
*/
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class RaplaClientServiceImpl implements ClientService, UpdateErrorListener, Disposable, UserClientService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RaplaClientServiceImpl.class);

    private final RemoteOperator operator;
    Vector<RaplaClientListener> listenerList = new Vector<>();
    RaplaResources i18n;
    boolean started;
    boolean restartingGUI;
    boolean defaultLanguageChosen;
    boolean logoutAvailable;
    ConnectInfo reconnectInfo;
    final StartupEnvironment env;
    final DialogUiFactoryInterface dialogUiFactory;
    final ClientFacade facade;
    final RaplaLocale raplaLocale;
    final BundleManager bundleManager;
    final CommandScheduler commandScheduler;
    org.rapla.scheduler.Cancellation schedule;

    Application application;
    final private Supplier<Application> applicationProvider;
    OAuth2PasswordLogin passwordLogin;
    RemoteConnectionInfo connectionInfo;
    final org.rapla.storage.dbrm.TokenStore tokenStore;
    final org.rapla.client.event.RaplaEventBus eventBus;
    final org.rapla.client.spring.LogoutSignal logoutSignal;
    @Autowired
    org.rapla.storage.dbrm.ImpersonationService impersonationService;
    /** Set by logout(); read+cleared by the next runOauthLogin call. Adds
     *  prompt=login to the authorize URL to defeat the cookie-reuse race
     *  when logout-and-restart happen in quick succession in the same JVM. */
    private volatile boolean nextOauthForcesLogin = false;

    /** The login dialog's language chooser for the current login attempt, so a
     *  successful login can persist the chosen language (PRD 029 Phase 4). */
    private LanguageChooser activeLanguageChooser;

    @Autowired
    public RaplaClientServiceImpl(StartupEnvironment env, DialogUiFactoryInterface dialogUiFactory, ClientFacade facade, RaplaResources i18n, RaplaSystemInfo systemInfo,
                                  RaplaLocale raplaLocale, BundleManager bundleManager, CommandScheduler commandScheduler, final RemoteOperator storageOperator,
                                  Supplier<Application> applicationProvider, RemoteConnectionInfo connectionInfo, OAuth2PasswordLogin passwordLogin,
                                  org.rapla.storage.dbrm.TokenStore tokenStore, org.rapla.client.event.RaplaEventBus eventBus,
                                  org.rapla.client.spring.LogoutSignal logoutSignal)
    {
        this.tokenStore = tokenStore == null ? org.rapla.storage.dbrm.TokenStores.noOp() : tokenStore;
        this.env = env;
        this.passwordLogin = passwordLogin;
        this.i18n = i18n;
        String version = systemInfo.getString("rapla.version");
        LOGGER.info("Rapla.Version={}", version);
        version = systemInfo.getString("rapla.build");
        LOGGER.info("Rapla.Build={}", version);
        try
        {
            String javaversion = System.getProperty("java.version");
            LOGGER.info("Java.Version={}", javaversion);
        }
        catch (SecurityException ex)
        {
            LOGGER.warn("Permission to system property java.version is denied!");
        }
        this.dialogUiFactory = dialogUiFactory;
        this.facade = facade;
        this.operator = storageOperator;
        this.raplaLocale = raplaLocale;
        this.bundleManager = bundleManager;
        this.commandScheduler = commandScheduler;
        this.applicationProvider = applicationProvider;
        this.eventBus = eventBus;
        this.logoutSignal = logoutSignal;
        ((ClientFacadeImpl) this.facade).setOperator(storageOperator);
        this.connectionInfo = connectionInfo;
        try
        {
            URL downloadURL = env.getDownloadURL();
            String baseUrl = downloadURL.toExternalForm();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            connectionInfo.setServerURL(baseUrl);
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }
        initialize();
    }

    protected void initialize()
    {
        advanceLoading(false);
        int startupMode = env.getStartupMode();
        if (startupMode != StartupEnvironment.WEBSTART)
        {
            try
            {
                Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                    LOGGER.error("uncaught exception", e);
                    if ( e instanceof IllegalMonitorStateException)
                    {
                        System.exit(-1);
                    }

                });
            }
            catch (Throwable ex)
            {
                LOGGER.error("Can't set default exception handler-", ex);
            }
        }

        ApplicationViewSwing.setLookandFeel();
        defaultLanguageChosen = true;
        LOGGER.info("Starting gui ");

        //Add this service to the container

    }

    public ClientFacade getClientFacade()
    {
        return facade;
    }

    @Override
    public void setImpersonation(String impersonationAccessToken, String targetUsername)
    {
        if (impersonationAccessToken == null || impersonationAccessToken.isEmpty()) return;
        connectionInfo.setImpersonationAccessToken(impersonationAccessToken, targetUsername);
        LOGGER.info("impersonation override applied for target user '{}'", targetUsername);
    }

    public void start(ConnectInfo connectInfo) throws Exception
    {
        if (started)
            return;
        LOGGER.debug("RaplaClient started");
        ClientFacade facade = getClientFacade();
        facade.addUpdateErrorListener(this);
        // Mid-session refresh-on-401 hook: when the auth seam decides the cached
        // refresh token is dead too, route through the existing facade
        // "disconnected" pipeline so the user sees the re-login dialog instead
        // of a silently broken calendar. Without this the calendar would just
        // log "401 : [no body]" and freeze (see disconnected(...) below).
        connectionInfo.setOnAuthDead(() -> {
            LOGGER.warn("session_expired: access + refresh tokens both rejected — prompting re-login");
            // tokenStore holds the persisted refresh token from a previous launch —
            // drop it too so the next start() opens the login dialog instead of
            // trying a silent reauth that will fail the same way.
            try { tokenStore.tryClear(); } catch (Exception ignored) {}
            // Route through the existing disconnected() pipeline (modal dialog →
            // restart() → login flow). The hook can fire on any thread; the
            // dialog code already invokeLater's onto the EDT.
            this.disconnected(i18n.getString("restart_client"));
        });
        advanceLoading(true);

        logoutAvailable = true;
        // PRD 029 Phase 5: ConnectInfo carries tokens only. Non-null means the
        // launcher supplied an access token (CLI args carrying an API JWT, OAuth
        // tokens from a previous iteration's switch-to-user, etc.); null means
        // "no credentials, show login dialog".
        if (connectInfo != null)
        {
            login(connectInfo).thenAccept( (result)-> {
                LOGGER.info("Login successfull");
                if (result )
                    beginRaplaSession();
                else
                    startLogin();
            }).exceptionally( ex ->
            {
                LOGGER.error(ex.getMessage(), ex);
                startLogin();
            });
        } else {
            startLogin();
        }
    }

    protected void advanceLoading(boolean finish)
    {
        try
        {
            Class<?> LoadingProgressC = null;
            Object progressBar = null;
            if (env.getStartupMode() == StartupEnvironment.CONSOLE)
            {
                LoadingProgressC = getClass().getClassLoader().loadClass("org.rapla.bootstrap.LoadingProgress");
                progressBar = LoadingProgressC.getMethod("inject").invoke(null);
                if (finish)
                {
                    LoadingProgressC.getMethod("close").invoke(progressBar);
                }
                else
                {
                    LoadingProgressC.getMethod("advance").invoke(progressBar);
                }
            }
        }
        catch (Exception ex)
        {
            // Loading progress failure is not crucial to rapla excecution
        }
    }

    /**
     * @throws RaplaException
     *
     */
    private Promise<Void> beginRaplaSession()
    {
        return getClientFacade().load().thenRun(()->
        {
            initRefresh();
            String language = null;
            if ( !defaultLanguageChosen)
            {
                language = bundleManager.getLocale().getLanguage();
            }
            String localeId = facade.getRaplaFacade().getSystemPreferences().getEntryAsString(AbstractRaplaLocale.LOCALE, null);
            if ( localeId != null)
            {
                final Locale locale = LocaleTools.getLocale(localeId);
                ((SwingBundleManager) bundleManager).setLocale(locale);
                if ( language != null)
                {
                    bundleManager.setLanguage(language);
                }
            }

            application = applicationProvider.get();
            application.start(defaultLanguageChosen, () ->
            {
                if (!isRestartingGUI()) {
                    stop();
                    // PRD 052 Phase 2 — "Beenden" (Exit) menu published the
                    // CLOSE_ACTIVITY_ID event; Application.startAction called
                    // this closeCallback after mainView.close(). Now signal
                    // SpringRaplaClient.main() to break its context loop so
                    // the JVM exits cleanly instead of blocking on take().
                    logoutSignal.next(org.rapla.client.spring.NextSession.exit());
                } else {
                    restartingGUI = false;
                }
            });
            started = true;
            fireClientStarted();
        }).exceptionally((ex)->
                {
                    LOGGER.error(ex.getMessage(),ex);
                    try {
                        closeApplication();
                    } finally {
                        fireClientClosed(null);
                    }
                }
        );
    }

    public boolean isRestartingGUI()
    {
        return restartingGUI;
    }

    public void addRaplaClientListener(RaplaClientListener listener)
    {
        listenerList.add(listener);
    }

    public void removeRaplaClientListener(RaplaClientListener listener)
    {
        listenerList.remove(listener);
    }

    public RaplaClientListener[] getRaplaClientListeners()
    {
        return listenerList.toArray(new RaplaClientListener[] {});
    }

    protected void fireClientClosed(ConnectInfo reconnect)
    {
        RaplaClientListener[] listeners = getRaplaClientListeners();
        for (int i = 0; i < listeners.length; i++)
            listeners[i].clientClosed(reconnect);
    }

    protected void fireClientStarted()
    {
        RaplaClientListener[] listeners = getRaplaClientListeners();
        for (int i = 0; i < listeners.length; i++)
            listeners[i].clientStarted();
    }

    protected void fireClientAborted()
    {
        RaplaClientListener[] listeners = getRaplaClientListeners();
        for (int i = 0; i < listeners.length; i++)
            listeners[i].clientAborted();
    }

    public void notifyLoginAborted()
    {
        logoutSignal.next(org.rapla.client.spring.NextSession.exit());
    }

    private static Throwable rootCause(Throwable ex)
    {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root;
    }

    private static boolean isServerUnreachable(Throwable ex)
    {
        Throwable root = rootCause(ex);
        return root instanceof java.net.ConnectException
                || root instanceof java.net.UnknownHostException
                || root instanceof java.net.NoRouteToHostException
                || root instanceof java.net.http.HttpConnectTimeoutException
                || root instanceof java.net.http.HttpTimeoutException;
    }

    private void abortWithConnectError(Throwable ex)
    {
        Throwable root = rootCause(ex);
        String serverUrl = connectionInfo.getServerURL();
        String message = i18n.format("error.connect", serverUrl) + " " + root.getMessage();
        LOGGER.warn("startup: {}", message);
        org.rapla.storage.dbrm.RaplaConnectException friendly = new org.rapla.storage.dbrm.RaplaConnectException(message);
        SwingSafe.invokeLater(() -> {
            dialogUiFactory.showException(friendly, null);
            fireClientAborted();
        });
    }

    public boolean isRunning()
    {
        return started;
    }

    /**
     * PRD 051 — admin "switch to user". Calls
     * {@code POST /api/auth/impersonate} via the {@link
     * org.rapla.storage.dbrm.ImpersonationService} proxy, stashes the
     * returned access token on {@link RemoteConnectionInfo} as the
     * effective Bearer, then refreshes the facade so the UI repaints
     * against the impersonated identity. {@code switchTo(null)} clears
     * the impersonation token; outbound requests revert to the admin's
     * own Bearer on the very next call.
     *
     * <p>No login restart, no stashed admin password, no
     * password-based reconnect — the admin's tokens stay in their
     * normal slot throughout. See PRD 051 § "Token renewal model".
     */
    public void switchTo(User user) throws RaplaException
    {
        if (user == null)
        {
            // PRD 052 Phase 2 + PRD 029 Phase 5 — "are we impersonating?" still
            // comes from LogoutSignal even though the dual-slot model is now
            // wired correctly. LogoutSignal carries the boolean across the
            // context boundary because the launcher (SpringRaplaClient.main)
            // is the source of truth for "this iteration was started as an
            // impersonation session"; reading connectionInfo.isImpersonating()
            // works too but couples this decision to the post-start setter
            // ordering. Keep the signal-based flag.
            if (!logoutSignal.isImpersonationSession())
            {
                throw new RaplaException("Not currently switched to another user.");
            }
            LOGGER.info("Switching back to admin — closing impersonation context, restoring admin session");
            // Fire the close+recreate signal; main() restores the previously-saved
            // admin ConnectInfo on the next iteration.
            logoutSignal.next(org.rapla.client.spring.NextSession.switchBack());
        }
        else
        {
            if (logoutSignal.isImpersonationSession())
            {
                throw new RaplaException("Already switched to a user; switch back first.");
            }
            org.rapla.storage.dbrm.ImpersonationResponse resp = impersonationService.impersonate(user.getUsername());
            if (resp == null || resp.getAccessToken() == null || resp.getAccessToken().isEmpty())
            {
                throw new RaplaException("Impersonation endpoint returned no access token.");
            }
            LOGGER.info("Switching from admin to '{}' — closing admin context, opening impersonation context", user.getUsername());
            // PRD 052 Phase 2 — close the admin context and build a fresh one
            // for the impersonation session. Avoids the "non system preferences
            // for other users" cache-integrity error that the old in-place
            // token swap hit because admin's LocalCache contained data the
            // impersonated user shouldn't see.
            //
            // PRD 029 Phase 5 dual-slot — the new context starts with the
            // ADMIN's full session as primary (so refresh + impersonation
            // renewal both work), then sets the impersonation token in the
            // dedicated impersonation slot via ClientService.setImpersonation().
            // PRD 072 Phase 5: refresh always hits rapla's /oauth2/token, so the
            // admin session is fully captured by its two tokens — no provider
            // routing to carry across the context restart.
            String impersonationAccessToken = resp.getAccessToken();
            String targetUsername = user.getUsername();
            // Capture admin's FULL session NOW — connectionInfo dies with the context.
            ConnectInfo adminFullInfo = new ConnectInfo(
                    connectionInfo.getAccessToken(),
                    connectionInfo.getRefreshToken());
            logoutSignal.next(org.rapla.client.spring.NextSession.switchTo(
                    adminFullInfo, impersonationAccessToken, targetUsername));
        }
    }

    public boolean canSwitchBack()
    {
        // PRD 052 Phase 2 — under the close+recreate model the impersonation
        // session has a fresh connectionInfo without a dual-slot impersonation
        // token; the launcher (SpringRaplaClient.main) flags the new context
        // as an impersonation session via LogoutSignal. Read from there.
        return logoutSignal.isImpersonationSession();
    }

    private void stop()
    {
        stop(null);
    }

    private void stop(ConnectInfo reconnect)
    {
        if ( !started)
        {
            return;
        }
        try
        {
            closeApplication();
        }
        catch (Throwable ex)
        {
            LOGGER.error("Clean logout failed. {}", ex.getMessage());
        }
        started = false;
        fireClientClosed(reconnect);
    }

    private void closeApplication() throws RaplaException {
        if (application != null) {
            application.stop();
            RaplaGUIComponent.setMainComponent(null);
            ClientFacade facade = getClientFacade();
            if (facade != null) {
                facade.removeUpdateErrorListener(this);
                if (facade.isSessionActive()) {
                    facade.logout();
                }
            }
        }
    }

    public void dispose()
    {
        ((SwingSchedulerImpl) commandScheduler).cancel();
        stop();

        LOGGER.debug("RaplaClient disposed");
    }

    private void startLogin() throws Exception
    {
        // PRD 029 Phase 2 + the swing-legacy-login admin flag tied together:
        //   swing-legacy-login=true  → always show the login dialog, no silent reauth.
        //   swing-legacy-login=false → try silent reauth from the cached refresh token;
        //                              fall back to startLoginInThread (which auto-fires
        //                              the browser OAuth flow) on any failure.
        // Discovery failure or OAuth disabled server-side is treated as legacy mode —
        // there's no OAuth refresh endpoint to call against, so skip straight to the
        // dialog.
        LOGGER.info("startup: probing /api/auth/oauth/config to decide silent-reauth vs login dialog…");
        commandScheduler.supply(this::fetchOauthConfig)
                .thenAccept(cfg -> {
                    boolean silentReauthAllowed = cfg != null && cfg.isEnabled() && !cfg.isSwingLegacyLogin();
                    if (!silentReauthAllowed)
                    {
                        LOGGER.info("startup: silent reauth disabled ({}) — showing login dialog",
                                (cfg == null ? "no discovery"
                                        : !cfg.isEnabled() ? "OAuth disabled server-side"
                                        : "swing-legacy-login=true"));
                        SwingSafe.invokeLater(this::startLoginInThread);
                        return;
                    }
                    commandScheduler.supply(this::tryRestoreFromCachedRefreshToken)
                            .thenAccept(restored -> {
                                if (restored)
                                {
                                    LOGGER.info("startup: silent reauth succeeded — main view loading, no dialog");
                                    SwingSafe.invokeLater(this::beginRaplaSessionAfterRestore);
                                }
                                else
                                {
                                    LOGGER.info("startup: silent reauth did not restore — falling through to login dialog");
                                    SwingSafe.invokeLater(this::startLoginInThread);
                                }
                            })
                            .exceptionally(ex -> {
                                LOGGER.info("startup: silent reauth failed ({}) — falling through to login dialog", ex.getMessage());
                                SwingSafe.invokeLater(this::startLoginInThread);
                            });
                })
                .exceptionally(ex -> {
                    if (isServerUnreachable(ex))
                    {
                        abortWithConnectError(ex);
                        return;
                    }
                    LOGGER.info("startup: discovery failed ({}) — showing login dialog", ex.getMessage());
                    SwingSafe.invokeLater(this::startLoginInThread);
                });
    }

    /**
     * Reads the cached refresh token (if any), calls the refresh endpoint to mint
     * a fresh access token, and stashes both on the connectionInfo. Returns true
     * if the user is now logged in; false to fall through to the login dialog.
     * Never throws — any failure falls through.
     *
     * <p>PRD 072 Phase 5: rapla is Swing's single token endpoint, so the cached
     * refresh token is always replayed against {@code serverURL + /oauth2/token}
     * with {@code client_id=rapla-client} — no per-provider routing.
     */
    private boolean tryRestoreFromCachedRefreshToken()
    {
        java.util.Optional<String> cached;
        try
        {
            cached = tokenStore.read();
        }
        catch (Throwable t)
        {
            LOGGER.info("startup: token-store read failed: {} — falling back to login dialog", t);
            return false;
        }
        if (cached.isEmpty())
        {
            LOGGER.info("startup: no cached refresh token (first launch or after logout) — login dialog expected");
            return false;
        }
        String cachedRefresh = cached.get();
        LOGGER.info("startup: cached refresh token found — attempting silent reauth");
        try
        {
            String serverUrl = connectionInfo.getServerURL();
            if (serverUrl == null || serverUrl.isEmpty())
            {
                LOGGER.info("startup: server URL not yet set — falling back to login dialog");
                return false;
            }
            String trimmed = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
            String tokenUrl = trimmed + "/oauth2/token";
            SilentRefreshResult result = executeSilentRefresh(tokenUrl, "rapla-client", cachedRefresh);
            if (!result.succeeded())
            {
                LOGGER.info("startup: cached refresh token rejected (HTTP {} from {}) — clearing cache and falling back to login dialog",
                        result.httpStatus, tokenUrl);
                tokenStore.tryClear();
                return false;
            }
            ConnectInfo info = new ConnectInfo(result.accessToken, result.refreshToken);
            reconnectInfo = info;
            connectionInfo.setAccessToken(result.accessToken);
            if (result.refreshToken != null)
            {
                connectionInfo.setRefreshToken(result.refreshToken);
                tokenStore.tryWrite(result.refreshToken);
            }
            connectionInfo.setReconnectInfo(info);
            LOGGER.info("startup: silent reauth via cached refresh token succeeded against {} — skipping login dialog", tokenUrl);
            return true;
        }
        catch (Throwable t)
        {
            LOGGER.info("startup: refresh HTTP call failed: {} — falling back to login dialog", t);
            return false;
        }
    }

    private static String extractJson(String body, String field)
    {
        if (body == null) return null;
        String marker = "\"" + field + "\"";
        int i = body.indexOf(marker);
        if (i < 0) return null;
        int colon = body.indexOf(':', i + marker.length());
        if (colon < 0) return null;
        int firstQuote = body.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int closingQuote = body.indexOf('"', firstQuote + 1);
        if (closingQuote < 0) return null;
        return body.substring(firstQuote + 1, closingQuote);
    }

    /**
     * Pure HTTP-level OAuth2 refresh-token request, extracted for testability.
     * Posts {@code grant_type=refresh_token&refresh_token=...&client_id=...} to
     * {@code tokenUrl} and parses the snake_case OAuth2 response into a pair of
     * tokens. Returns {@code null} when the server responds non-2xx OR the body
     * lacks {@code access_token}.
     *
     * <p>Package-private + static so {@code RaplaClientServiceImplSilentReauthHelperTest}
     * can exercise it without building the whole Swing client context.
     */
    static SilentRefreshResult executeSilentRefresh(String tokenUrl, String clientId, String refreshToken) throws Exception
    {
        String body = "grant_type=refresh_token&refresh_token="
                + java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8)
                + "&client_id=" + java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8);
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)).build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(tokenUrl))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                .build();
        java.net.http.HttpResponse<String> resp = http.send(req,
                java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) return new SilentRefreshResult(resp.statusCode(), null, null);
        String respBody = resp.body();
        String newAccess = extractJson(respBody, "access_token");
        if (newAccess == null) return new SilentRefreshResult(resp.statusCode(), null, null);
        String newRefresh = extractJson(respBody, "refresh_token");
        return new SilentRefreshResult(resp.statusCode(), newAccess, newRefresh);
    }

    /** Outcome of a silent-refresh attempt. {@code accessToken == null} when the
     *  refresh failed (non-2xx, or 2xx but missing access_token). */
    static final class SilentRefreshResult
    {
        final int httpStatus;
        final String accessToken;
        final String refreshToken;
        SilentRefreshResult(int httpStatus, String accessToken, String refreshToken)
        {
            this.httpStatus = httpStatus;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
        boolean succeeded() { return accessToken != null; }
    }

    private void beginRaplaSessionAfterRestore()
    {
        beginRaplaSession().exceptionally(ex -> {
            LOGGER.error("post-restore session start failed; falling back to login dialog", ex);
            // Drop the cached token if the session can't start with it for any reason
            tokenStore.tryClear();
            SwingSafe.invokeLater(this::startLoginInThread);
        });
    }

    private void startLoginInThread()
    {
        final Semaphore loginMutex = new Semaphore(1);
        try
        {
            final Logger logger = LOGGER;
            final AbstractBundleManager localeSelector = (AbstractBundleManager) bundleManager;
            // PRD 029 Phase 4: restore the language used at the last successful
            // login (TokenStore pref) before building the dialog, so it renders
            // in that language straight away.
            final String savedLanguage = readLoginPref(TokenStore.KEY_LANGUAGE);
            if (!savedLanguage.isEmpty())
            {
                try { localeSelector.setLanguage(savedLanguage); }
                catch (Exception ex) { LOGGER.debug("could not restore saved language '{}': {}", savedLanguage, ex); }
            }
            final LanguageChooser languageChooser = new LanguageChooser(i18n, raplaLocale);
            activeLanguageChooser = languageChooser;
            final LoginDialog dlg = LoginDialog.create(env, i18n, localeSelector, raplaLocale, languageChooser.getComponent());
            // PRD 072 Phase 5: the per-provider menu is gone. In the legacy
            // password dialog the user may optionally also be offered a single
            // "SSO" method (the rapla SSO entry — index 1 in the method combo).
            // When that method is present this holds the rapla SSO config; null
            // means the dialog offers password only.
            final java.util.concurrent.atomic.AtomicReference<OAuthConfig> ssoConfig =
                    new java.util.concurrent.atomic.AtomicReference<>(null);

            Action languageChanged = new AbstractAction()
            {
                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent evt)
                {
                    try
                    {
                        String lang = languageChooser.getSelectedLanguage();
                        if (lang == null)
                        {
                            defaultLanguageChosen = true;
                        }
                        else
                        {
                            defaultLanguageChosen = false;
                            LOGGER.debug("Language changing to {}", lang);
                            localeSelector.setLanguage(lang);
                            LOGGER.info("Language changed {}", localeSelector.getLocale().getLanguage());
                        }
                    }
                    catch (Exception ex)
                    {
                        LOGGER.error("Can't change language", ex);
                    }
                }

            };
            languageChooser.setChangeAction(languageChanged);
            if (!savedLanguage.isEmpty())
            {
                languageChooser.setSelectedLanguage(savedLanguage);
            }
            //dlg.setIcon( i18n.getIcon("icon.rapla-small"));
            Action loginAction = new AbstractAction()
            {
                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent evt)
                {
                    // PRD 072 Phase 5: the Login button is method-aware. Index 0
                    // is the local username/password grant; index 1 (only present
                    // in the legacy dialog when SSO is also offered) is the single
                    // rapla SSO entry — rapla then federates the upstream IdP on
                    // its /login chooser page.
                    int methodIndex = dlg.getSelectedMethodIndex();
                    if (methodIndex > 0)
                    {
                        OAuthConfig sso = ssoConfig.get();
                        if (sso != null)
                        {
                            runOauthLogin(dlg, loginMutex, sso);
                        }
                        return;
                    }
                    final String username = dlg.getUsername();
                    char[] password = dlg.getPassword();
                    // PRD 029 Phase 5 (2026-05-25): dropped the legacy " su "
                    // syntax for in-dialog impersonation. The OAuth2 password
                    // grant has no connect_as parameter; the modern path is
                    // log in as yourself + admin menu "Switch to user" (uses
                    // /api/auth/impersonate via the dual-slot model). Typing
                    // "admin su other" now logs in as the literal username
                    // "admin su other" — which won't exist and surfaces as a
                    // normal login failure.
                    dlg.busy( i18n.getString("login"));
                    // PRD 029 Phase 5: the dialog is the password-flow user-input
                    // boundary. Exchange password→tokens here via the auth seam
                    // and from then on only token-bearing ConnectInfo flows.
                    // Password char[] is zeroed in the finally block of the
                    // background task — no reference survives past this lambda.
                    commandScheduler.supply(() -> {
                        org.rapla.storage.dbrm.LoginCredentials wireCreds =
                                new org.rapla.storage.dbrm.LoginCredentials(username, password);
                        try {
                            return passwordLogin.login(wireCreds);
                        } finally {
                            wireCreds.clearPassword();
                            if (password != null) java.util.Arrays.fill(password, '\0');
                        }
                    }).thenAccept(tokens -> SwingSafe.invokeLater(() -> {
                        if (tokens == null || tokens.getAccessToken() == null) {
                            dlg.resetPassword();
                            dlg.idle();
                            dialogUiFactory.showWarning(i18n.getString("error.login"), new SwingPopupContext(dlg, null));
                            return;
                        }
                        ConnectInfo info = new ConnectInfo(tokens.getAccessToken(), tokens.getRefreshToken());
                        reconnectInfo = info;
                        login(info).thenAccept(success -> SwingSafe.invokeLater(() -> {
                            // PRD 029 Phase 4: remember language + "password" method.
                            persistLoginPrefs("password");
                            dlg.idle();
                            loginMutex.release();
                            dlg.busy(i18n.getString("load"));
                            beginRaplaSession().thenRun(()->{dlg.idle();dlg.dispose();}).exceptionally( ex->
                            {
                                dialogUiFactory.showException(ex, null);
                                dlg.idle();
                                fireClientAborted();
                            });
                        })).exceptionally(ex -> SwingSafe.invokeLater(() -> {
                            dialogUiFactory.showException(ex, new SwingPopupContext(dlg, null));
                            dlg.idle();
                        }));
                    })).exceptionally(ex -> SwingSafe.invokeLater(() -> {
                        Throwable root = unwrap(ex);
                        if (root instanceof org.springframework.web.client.HttpClientErrorException.Unauthorized
                                || root instanceof org.rapla.storage.RaplaSecurityException) {
                            dlg.resetPassword();
                            dlg.idle();
                            dialogUiFactory.showWarning(i18n.getString("error.login"), new SwingPopupContext(dlg, null));
                        } else {
                            dlg.resetPassword();
                            dialogUiFactory.showException(ex, new SwingPopupContext(dlg, null));
                            dlg.idle();
                        }
                    }));
                }

            };
            Action exitAction = new AbstractAction()
            {
                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent evt)
                {
                    dlg.dispose();
                    loginMutex.release();
                    stop();
                    fireClientAborted();
                    notifyLoginAborted();
                }
            };
            loginAction.putValue(Action.NAME, i18n.getString("login"));
            exitAction.putValue(Action.NAME, i18n.getString("exit"));
            dlg.setIconImage(RaplaImages.getImage(i18n.getIcon("icon.rapla_small")));
            dlg.setLoginAction(loginAction);
            dlg.setExitAction(exitAction);
            centerWindowOnScreen(dlg);

            // PRD 072 Phase 5 (Swing A+Y): rapla is Swing's single federating
            // Authorization Server. The DEFAULT login is the single "SSO" entry —
            // when discovery says OAuth is enabled and the admin has NOT opted into
            // the legacy dialog (rapla.oauth.swing-legacy-login), the dialog goes
            // straight into "browser login in progress" mode and auto-launches the
            // rapla SSO flow (/oauth2/authorize → /login, where the provider chooser
            // now lives server-side). The rapla password / fallback path stays
            // AVAILABLE but is no longer the default: it shows only when the admin
            // sets swing-legacy-login (or OAuth is unavailable). The per-provider
            // menu is removed — at most one extra "SSO" method appears in the legacy
            // dialog (gated by swing-legacy-show-sso-button), pointing at the same
            // rapla SSO entry.
            commandScheduler.supply(this::fetchOauthConfig).thenAccept(cfg -> SwingSafe.invokeLater(() -> {
                boolean oauthEnabled = cfg != null && cfg.isEnabled();
                boolean legacyLogin = cfg != null && cfg.isSwingLegacyLogin();
                if (oauthEnabled && !legacyLogin)
                {
                    LOGGER.info("startup: discovery confirms OAuth enabled — auto-launching rapla SSO flow (Swing dialog stays in waiting mode)");
                    dlg.setBrowserLoginInProgress(i18n.getString("login.oauth.waiting"));
                    dlg.setVisible(true);
                    runOauthLogin(dlg, loginMutex, cfg);
                }
                else
                {
                    boolean offerSso = oauthEnabled && legacyLogin && cfg.isSwingLegacyShowSsoButton();
                    configureLegacyDialogMethods(dlg, offerSso ? cfg : null, ssoConfig);
                    LOGGER.info("startup: showing legacy Swing login dialog"
                            + (oauthEnabled ? " (admin set rapla.oauth.swing-legacy-login)" : " (OAuth disabled server-side)")
                            + (offerSso ? " with the SSO method" : ""));
                    dlg.setVisible(true);
                }
            })).exceptionally(ex -> {
                if (isServerUnreachable(ex))
                {
                    abortWithConnectError(ex);
                    return;
                }
                SwingSafe.invokeLater(() -> {
                    Throwable root = rootCause(ex);
                    LOGGER.info("startup: discovery failed ({}: {}) — showing legacy Swing login dialog as fallback", root.getClass().getSimpleName(), root.getMessage());
                    // Discovery failed — OAuth support can't be confirmed, so offer
                    // only the local password method.
                    configureLegacyDialogMethods(dlg, null, ssoConfig);
                    dlg.setVisible(true);
                });
            });

            loginMutex.acquire();
        }
        catch (Exception ex)
        {
            LOGGER.error("Error during Login ", ex);
            stop();
            fireClientAborted();
        }
        finally
        {
            loginMutex.release();
        }
    }

    /** Best-effort read of a stored login preference. Never throws. */
    private String readLoginPref(String key)
    {
        try { return tokenStore.readPref(key).orElse(""); }
        catch (Throwable t) { return ""; }
    }

    /** Persists the language + sign-in method of a successful login so the next
     *  launch can default the dialog to them (PRD 029 Phase 4). Best-effort. */
    private void persistLoginPrefs(String loginMethod)
    {
        try
        {
            LanguageChooser chooser = activeLanguageChooser;
            String lang = chooser != null ? chooser.getSelectedLanguage() : null;
            // null = "default" — stored as empty, which clears the pref.
            tokenStore.tryWritePref(TokenStore.KEY_LANGUAGE, lang == null ? "" : lang);
            if (loginMethod != null && !loginMethod.isEmpty())
            {
                tokenStore.tryWritePref(TokenStore.KEY_LOGIN_METHOD, loginMethod);
            }
        }
        catch (Throwable t)
        {
            LOGGER.debug("could not persist login prefs: {}", t);
        }
    }

    /**
     * PRD 072 Phase 5: populate the legacy dialog's method combo. Index 0 is
     * always the local username/password grant. When {@code sso} is non-null
     * (the rapla SSO config, offered via swing-legacy-show-sso-button) a single
     * "SSO" method is added at index 1 and stashed in {@code ssoConfig} so the
     * Login button can launch it; picking it greys out the credential fields.
     * When {@code sso} is null only the password method is shown.
     */
    private void configureLegacyDialogMethods(LoginDialog dlg, OAuthConfig sso,
            java.util.concurrent.atomic.AtomicReference<OAuthConfig> ssoConfig)
    {
        java.util.List<String> labels = new java.util.ArrayList<>();
        labels.add(i18n.getString("password"));
        boolean ssoOffered = sso != null;
        if (ssoOffered)
        {
            labels.add("SSO");
            ssoConfig.set(sso);
        }
        else
        {
            ssoConfig.set(null);
        }
        dlg.setLoginMethods(labels);
        dlg.setMethodChangeListener(e -> dlg.setCredentialsEnabled(dlg.getSelectedMethodIndex() == 0));

        // PRD 072 Phase 5: when SSO is offered it is the DEFAULT method, and the
        // last-used method is remembered across launches (persistLoginPrefs writes
        // "sso"/"password" on a successful login). Index 0 = password, index 1 = SSO.
        String saved = tokenStore.readPref(TokenStore.KEY_LOGIN_METHOD).orElse("");
        int selected;
        if ("password".equals(saved) || !ssoOffered)
        {
            selected = 0;                  // explicit last choice, or SSO not available
        }
        else
        {
            selected = 1;                  // remembered "sso", or no preference yet → SSO default
        }
        dlg.setSelectedMethodIndex(selected);
        dlg.setCredentialsEnabled(selected == 0);
    }

    private void runOauthLogin(LoginDialog dlg, Semaphore loginMutex, OAuthConfig provider)
    {
        // While the browser-login flow runs, the dialog sits in "waiting for
        // browser sign-in" mode. Abort cancels the SwingOAuthLoginFlow session
        // (CancellationException → the loopback listener is stopped via the
        // flow's whenComplete) and the exceptionally branch below restores the
        // credential dialog. Distinct from Exit, which quits the application.
        final java.util.concurrent.atomic.AtomicReference<SwingOAuthLoginFlow.Session> sessionRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean aborted =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        dlg.setAbortAction(new AbstractAction(i18n.getString("abort"))
        {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent evt)
            {
                LOGGER.info("OAuth login: user clicked Abort — cancelling the browser-login wait");
                aborted.set(true);
                SwingOAuthLoginFlow.Session s = sessionRef.get();
                if (s != null)
                {
                    s.future().cancel(true);
                }
                else
                {
                    // flow.start() hasn't returned yet (discovery probe still
                    // in flight). Restore the dialog now; the worker cancels
                    // the session as soon as it is created (aborted check below).
                    dlg.clearBrowserLoginInProgress();
                }
            }
        });
        dlg.setBrowserLoginInProgress(i18n.getString("login.oauth.waiting"));
        commandScheduler.supply(() -> {
            // PRD 072 Phase 5: the single SSO entry always targets rapla's own
            // Authorization Server (/oauth2/authorize → /login chooser). rapla
            // brokers the upstream IdP and returns a rapla-issuer token; refresh
            // therefore always hits rapla's /oauth2/token — no provider routing
            // to stash on connectionInfo.
            LOGGER.info("OAuth login: starting browser flow via rapla SSO");
            if (provider.getLogoutUrl() != null)
            {
                connectionInfo.setLogoutUrl(provider.getLogoutUrl());
            }
            SwingOAuthLoginFlow flow = new SwingOAuthLoginFlow(provider);
            boolean force = nextOauthForcesLogin;
            nextOauthForcesLogin = false;
            if (force)
            {
                flow.forceLogin(true);
                LOGGER.info("OAuth flow: forcing IdP login (prompt=login) — post-logout restart");
            }
            SwingOAuthLoginFlow.Session session = flow.start();
            sessionRef.set(session);
            if (aborted.get())
            {
                // User clicked Abort during the discovery probe, before the
                // session existed — honour it now.
                session.future().cancel(true);
            }
            return session.future().get();
        }).thenAccept(tokens -> SwingSafe.invokeLater(() -> finishOauthLogin(dlg, loginMutex, tokens, provider)))
                .exceptionally(ex -> SwingSafe.invokeLater(() -> {
                    Throwable root = unwrap(ex);
                    if (root instanceof java.util.concurrent.CancellationException)
                    {
                        LOGGER.info("OAuth login cancelled — restoring Swing login dialog to full state");
                        dlg.idle();
                        dlg.clearBrowserLoginInProgress();
                        if (!dlg.isVisible()) dlg.setVisible(true);
                        return;
                    }
                    LOGGER.error("OAuth login failed — restoring Swing login dialog to full state", ex);
                    dlg.idle();
                    dlg.clearBrowserLoginInProgress();
                    if (!dlg.isVisible()) dlg.setVisible(true);
                    dialogUiFactory.showException(ex, new SwingPopupContext(dlg, null));
                }));
    }

    private static Throwable unwrap(Throwable t)
    {
        Throwable current = t;
        while (current.getCause() != null && current != current.getCause())
        {
            if (current instanceof java.util.concurrent.CancellationException) return current;
            current = current.getCause();
        }
        return current;
    }

    private void finishOauthLogin(LoginDialog dlg, Semaphore loginMutex, OAuthTokens tokens, OAuthConfig provider)
    {
        // PRD 072 Phase 5: the SSO flow yields a rapla-issuer token and refresh
        // always hits rapla's /oauth2/token, so the session is fully captured by
        // its two tokens — no provider routing to carry across a switch-back.
        ConnectInfo info = new ConnectInfo(tokens.getAccessToken(), tokens.getRefreshToken());
        reconnectInfo = info;
        // Capture the id_token for use as id_token_hint when the user signs out.
        // Without it, /connect/logout rejects the request (404) and the
        // rapla-remember-me cookie is not cleared → next OAuth flow silently
        // re-authenticates via the surviving cookie.
        connectionInfo.setIdToken(tokens.getIdToken());
        login(info).thenAccept(success -> {
            if (!success)
            {
                dlg.idle();
                dialogUiFactory.showWarning(i18n.getString("error.login"), new SwingPopupContext(dlg, null));
                return;
            }
            // Remember the language + SSO method for next launch.
            persistLoginPrefs("sso");
            dlg.idle();
            loginMutex.release();
            dlg.busy(i18n.getString("load"));
            beginRaplaSession().thenRun(() -> {
                dlg.idle();
                dlg.dispose();
            }).exceptionally(ex -> {
                dialogUiFactory.showException(ex, null);
                dlg.idle();
                fireClientAborted();
            });
        }).exceptionally(ex -> {
            dialogUiFactory.showException(ex, new SwingPopupContext(dlg, null));
            dlg.idle();
        });
    }

    private OAuthConfig fetchOauthConfig() throws Exception
    {
        URI discovery = URI.create(connectionInfo.getServerURL() + "/api/auth/oauth/config");
        HttpRequest req = HttpRequest.newBuilder(discovery)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2)
        {
            throw new IllegalStateException("OAuth discovery failed: HTTP " + resp.statusCode());
        }
        tools.jackson.databind.JsonNode tree = tools.jackson.databind.json.JsonMapper.builder().build().readTree(resp.body());
        boolean enabled = tree.path("enabled").asBoolean(false);
        if (!enabled)
        {
            return new OAuthConfig(false, null, null, null, List.of());
        }
        List<String> scopes = new java.util.ArrayList<>();
        if (tree.has("scopes") && tree.get("scopes").isArray())
        {
            tree.get("scopes").forEach(n -> scopes.add(n.asString()));
        }
        // PRD 041: discovery dropped refreshUrl — refresh uses tokenUrl
        // (/oauth2/token grant_type=refresh_token, OAuth 2.1 standard).
        String logoutUrl = tree.has("logoutUrl") && !tree.get("logoutUrl").isNull()
                ? tree.get("logoutUrl").asString() : null;
        // PRD 029 Phase 3: admin-selectable legacy Swing login. Absent on older
        // servers — default false keeps the OAuth-first behaviour.
        boolean swingLegacyLogin = tree.path("swingLegacyLogin").asBoolean(false);
        boolean swingLegacyShowSsoButton = tree.path("swingLegacyShowSsoButton").asBoolean(false);
        // PRD 072 Phase 5: rapla is Swing's single federating Authorization
        // Server. The Swing login no longer offers a per-provider menu — it has
        // one SSO entry pointing at rapla's own /oauth2/authorize → /login, where
        // the chooser now lives server-side. The discovery providers[] array is
        // therefore not consumed by Swing anymore (the server still publishes it
        // for the SPA / explorers); Swing only needs the top-level rapla config.
        return new OAuthConfig(
                true,
                tree.path("clientId").asString(),
                tree.path("authorizeUrl").asString(),
                tree.path("tokenUrl").asString(),
                logoutUrl,
                scopes,
                swingLegacyLogin,
                swingLegacyShowSsoButton,
                "rapla",
                null,
                List.of());
    }

    /** centers the window around the specified center */
    static public void centerWindowOnScreen(Window window) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension preferredSize = window.getSize();
        int x = screenSize.width/2 - (preferredSize.width / 2);
        int y = screenSize.height/2 - (preferredSize.height / 2);
        fitIntoScreen(x,y,window);
    }

    /** Tries to place the window, that it fits into the screen. */
    static public void fitIntoScreen(int x, int y, Component window) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension windowSize = window.getSize();
        if (x + windowSize.width > screenSize.width)
            x =  screenSize.width - windowSize.width;

        if (y + windowSize.height > screenSize.height)
            y =  screenSize.height - windowSize.height;

        if (x<0) x = 0;
        if (y<0) y = 0;
        window.setLocation(x,y);
    }


    private void initRefresh() throws RaplaException {
        int intervalLength = facade.getRaplaFacade().getSystemPreferences().getEntryAsInteger(ClientFacade.REFRESH_INTERVAL_ENTRY, ClientFacade.REFRESH_INTERVAL_DEFAULT);
        schedule = commandScheduler.schedule(()->operator.triggerRefresh(), 0, intervalLength);
    }

    public void updateError(RaplaException ex)
    {
        LOGGER.error("Error updating data", ex);
    }

    public void disconnected(final String message)
    {
        if (schedule != null) {
            schedule.cancel();
        }
        this.schedule = null;
        if (started)
        {
            SwingSafe.invokeLater(() -> {
                boolean modal = false;
                String title = i18n.getString("restart_client");
                try
                {
                    Component owner = null;
                    final DialogInterface dialog = dialogUiFactory.createInfoDialog(new SwingPopupContext(owner, null), title, message);
                    dialog.setCloseAction(()->
                    {
                        LOGGER.warn("restart");
                        dialog.close();
                        restart();
                    }
                    );
                    dialog.start(true);
                }
                catch (Throwable e)
                {
                    LOGGER.error(e.getMessage(), e);
                }

            });
        }
    }

    public void restart()
    {
        // The "Logout / Restart" menu action wires here. Users who click it
        // expect both: their session is revoked AND the client returns to a
        // login screen. The original implementation just stop()ed with the
        // existing reconnectInfo, which left the JVM with no UI and no
        // server-side session cleanup. Delegate to logout() so all the
        // session/token/browser-cookie cleanup fires uniformly.
        logout();
    }

    public void logout()
    {
        // Best-effort: tell the server to invalidate this user's session before
        // we drop the local tokens. If the server is unreachable the local
        // logout still proceeds — never block the user-visible logout on a
        // network call.
        // PRD 041: revoke via OAuth 2.0 standard /oauth2/revoke (RFC 7009)
        // with form-encoded body; replaces the legacy /api/auth/logout. The
        // server-side hook in AuthorizationServerConfig.RaplaTokenRevocationAuthenticationProvider
        // clears the user-prefs SESSION entry, invalidating every refresh
        // token in circulation for the user (single-token-per-user model).
        String refreshToken = connectionInfo.getRefreshToken();
        String serverUrl = connectionInfo.getServerURL();
        if (refreshToken != null && serverUrl != null && !serverUrl.isEmpty())
        {
            try
            {
                String body = "token=" + java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8)
                        + "&token_type_hint=refresh_token&client_id=rapla-client";
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(serverUrl + "/oauth2/revoke"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                        .build();
                HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() / 100 == 2)
                {
                    LOGGER.info("logout: server-side session revoked");
                }
                else
                {
                    LOGGER.info("logout: server returned HTTP {}; local logout proceeds", resp.statusCode());
                }
            }
            catch (Throwable t)
            {
                LOGGER.info("logout: server-side revocation failed ({}); local logout proceeds", t.getMessage());
            }
        }
        // No background browser tab to /connect/logout. In the M2 broker model that
        // endpoint only ends rapla's OWN SAS session (its id_token_hint is a rapla
        // token, not the upstream IdP's), it needs a non-expired hint (it 400s on a
        // stale one), and a tab popping up on logout is poor UX. The /oauth2/revoke
        // call above already invalidated the server-side session; the still-live
        // upstream IdP (e.g. Keycloak) SSO session is handled at the NEXT login by
        // prompt=login (nextOauthForcesLogin below + the server /login page forcing
        // a re-prompt for Keycloak), not by a logout redirect here.
        tokenStore.tryClear();
        // PRD 051 — discard any active impersonation. Logout is a clean
        // state-change boundary; surviving impersonation into the next
        // login session would surprise the next admin.
        connectionInfo.clearImpersonationToken();
        // PRD 052 Phase 1b — complete the current event-bus subjects so observers
        // disposed cleanly and the observer-list entries the subjects retain
        // don't accumulate across logouts. Recreates fresh subjects for the next
        // session. Becomes redundant when Phase 2 lands (the whole context dies
        // on logout — the bus's @PreDestroy fires for free).
        eventBus.reset();
        // Tell the next OAuth flow to force the IdP login form regardless of the
        // browser's session cookie. We no longer open a /connect/logout tab, so the
        // browser may still hold a live SSO session; prompt=login forces a fresh
        // prompt deterministically on the next /oauth2/authorize.
        nextOauthForcesLogin = true;
        stop(null);
        // PRD 052 Phase 2 — signal SpringRaplaClient.main() to close this
        // Spring context and build a fresh one for the next login. main()
        // disposes JFrames, runs ctx.close() (which fires DisposableBean /
        // @PreDestroy across every bean), and rebuilds the context. The user
        // sees a fresh login dialog in a fresh context with zero carry-over
        // of cached entities, UI state, or singleton listener registrations.
        logoutSignal.next(org.rapla.client.spring.NextSession.showLoginDialog());
    }

    /**
     * Token-only session start. PRD 072 Phase 5: ConnectInfo carries access
     * + refresh tokens only — refresh always hits rapla's /oauth2/token, so a
     * new context built after a switch-back (or any close+recreate boundary)
     * restores admin's session from the two tokens alone. The password→token
     * conversion happens at the user-input boundary (legacy dialog, CLI
     * bootstrap), never here.
     */
    private Promise<Boolean> login(ConnectInfo connectInfo)
    {
        return commandScheduler.supply(() -> {
            this.connectionInfo.setAccessToken(connectInfo.getAccessToken());
            this.connectionInfo.setRefreshToken(connectInfo.getRefreshToken());
            this.connectionInfo.setReconnectInfo(connectInfo);
            this.reconnectInfo = connectInfo;
            if (connectInfo.getRefreshToken() != null)
            {
                tokenStore.tryWrite(connectInfo.getRefreshToken());
                LOGGER.info("login: refresh token persisted for next launch (skip login dialog)");
            }
            else
            {
                LOGGER.warn("login: no refresh token received — next launch will require login again");
            }
            return true;
        });
    }

    public boolean isLogoutAvailable()
    {
        return logoutAvailable;
    }

}
