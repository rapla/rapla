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
import org.rapla.client.internal.OAuthCallbackPasteDialog;
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
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.TokenStore;
import org.rapla.storage.dbrm.RemoteAuthentificationService;
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

    private final RemoteOperator operator;
    Vector<RaplaClientListener> listenerList = new Vector<>();
    RaplaResources i18n;
    boolean started;
    boolean restartingGUI;
    boolean defaultLanguageChosen;
    boolean logoutAvailable;
    ConnectInfo reconnectInfo;
    final Logger logger;
    final StartupEnvironment env;
    final DialogUiFactoryInterface dialogUiFactory;
    final ClientFacade facade;
    final RaplaLocale raplaLocale;
    final BundleManager bundleManager;
    final CommandScheduler commandScheduler;
    org.rapla.scheduler.Cancellation schedule;

    Application application;
    final private Supplier<Application> applicationProvider;
    RemoteAuthentificationService authentificationService;
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
    public RaplaClientServiceImpl(StartupEnvironment env, Logger logger, DialogUiFactoryInterface dialogUiFactory, ClientFacade facade, RaplaResources i18n, RaplaSystemInfo systemInfo,
                                  RaplaLocale raplaLocale, BundleManager bundleManager, CommandScheduler commandScheduler, final RemoteOperator storageOperator,
                                  Supplier<Application> applicationProvider, RemoteConnectionInfo connectionInfo, RemoteAuthentificationService authentificationService,
                                  org.rapla.storage.dbrm.TokenStore tokenStore, org.rapla.client.event.RaplaEventBus eventBus,
                                  org.rapla.client.spring.LogoutSignal logoutSignal)
    {
        this.tokenStore = tokenStore == null ? org.rapla.storage.dbrm.TokenStores.noOp() : tokenStore;
        this.env = env;
        this.authentificationService = authentificationService;
        this.i18n = i18n;
        String version = systemInfo.getString("rapla.version");
        logger.info("Rapla.Version=" + version);
        version = systemInfo.getString("rapla.build");
        logger.info("Rapla.Build=" + version);
        try
        {
            String javaversion = System.getProperty("java.version");
            logger.info("Java.Version=" + javaversion);
        }
        catch (SecurityException ex)
        {
            logger.warn("Permission to system property java.version is denied!");
        }
        this.logger = logger;
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

    public Logger getLogger()
    {
        return logger;
    }

    protected void initialize()
    {
        advanceLoading(false);
        int startupMode = env.getStartupMode();
        final Logger logger = getLogger();
        if (startupMode != StartupEnvironment.WEBSTART)
        {
            try
            {
                Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                    logger.error("uncaught exception", e);
                    if ( e instanceof IllegalMonitorStateException)
                    {
                        System.exit(-1);
                    }

                });
            }
            catch (Throwable ex)
            {
                logger.error("Can't set default exception handler-", ex);
            }
        }

        ApplicationViewSwing.setLookandFeel();
        defaultLanguageChosen = true;
        getLogger().info("Starting gui ");

        //Add this service to the container

    }

    public ClientFacade getClientFacade()
    {
        return facade;
    }

    public void start(ConnectInfo connectInfo) throws Exception
    {
        if (started)
            return;
        getLogger().debug("RaplaClient started");
        ClientFacade facade = getClientFacade();
        facade.addUpdateErrorListener(this);
        // Mid-session refresh-on-401 hook: when the auth seam decides the cached
        // refresh token is dead too, route through the existing facade
        // "disconnected" pipeline so the user sees the re-login dialog instead
        // of a silently broken calendar. Without this the calendar would just
        // log "401 : [no body]" and freeze (see disconnected(...) below).
        connectionInfo.setOnAuthDead(() -> {
            getLogger().warn("session_expired: access + refresh tokens both rejected — prompting re-login");
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
        // Auto-login when the ConnectInfo carries any credential — username +
        // password (legacy), or an access token (PRD 052 Phase 2 switch-to-user
        // path: impersonation ConnectInfo has only an access token, no username).
        if (connectInfo != null && (connectInfo.getUsername() != null || connectInfo.getAccessToken() != null))
        {
            login(connectInfo).thenAccept( (result)-> {
                getLogger().info("Login successfull");
                if (result )
                    beginRaplaSession();
                else
                    startLogin();
            }).exceptionally( ex ->
            {
                getLogger().error(ex.getMessage(), ex);
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
                    logger.error(ex.getMessage(),ex);
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
            // PRD 052 Phase 2 — "are we impersonating?" comes from LogoutSignal,
            // not connectionInfo.hasImpersonationToken(). The close+recreate
            // model carries the impersonation token in the regular accessToken
            // slot, not the dual-slot the old in-place switchTo populated.
            if (!logoutSignal.isImpersonationSession())
            {
                throw new RaplaException("Not currently switched to another user.");
            }
            getLogger().info("Switching back to admin — closing impersonation context, restoring admin session");
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
            getLogger().info("Switching from admin to '" + user.getUsername() + "' — closing admin context, opening impersonation context");
            // PRD 052 Phase 2 — close the admin context and build a fresh one
            // for the impersonation session. Avoids the "non system preferences
            // for other users" cache-integrity error that the old in-place
            // token swap hit because admin's LocalCache contained data the
            // impersonated user shouldn't see.
            //
            // PRD 051 § "Token renewal model" — impersonation tokens are not
            // refreshable; the new ConnectInfo carries only the access token.
            // When it expires, the user re-clicks switch-to-user (or we add a
            // re-impersonate hook later).
            ConnectInfo impersonationInfo = ConnectInfo.withAccessToken(resp.getAccessToken(), null);
            // Capture admin's current credentials NOW — connectionInfo dies with
            // the context. Without this the launcher can't restore the admin
            // session on switch-back, especially when admin logged in via the
            // interactive dialog (then there's no startup-supplied ConnectInfo
            // to fall back to).
            ConnectInfo adminRestoreInfo = ConnectInfo.withAccessToken(
                    connectionInfo.getAccessToken(),
                    connectionInfo.getRefreshToken());
            logoutSignal.next(org.rapla.client.spring.NextSession.switchTo(impersonationInfo, adminRestoreInfo));
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
            getLogger().error("Clean logout failed. " + ex.getMessage());
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

        getLogger().debug("RaplaClient disposed");
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
        getLogger().info("startup: probing /api/auth/oauth/config to decide silent-reauth vs login dialog…");
        commandScheduler.supply(this::fetchOauthConfig)
                .thenAccept(cfg -> {
                    boolean silentReauthAllowed = cfg != null && cfg.isEnabled() && !cfg.isSwingLegacyLogin();
                    if (!silentReauthAllowed)
                    {
                        getLogger().info("startup: silent reauth disabled (" +
                                (cfg == null ? "no discovery"
                                        : !cfg.isEnabled() ? "OAuth disabled server-side"
                                        : "swing-legacy-login=true")
                                + ") — showing login dialog");
                        SwingSafe.invokeLater(logger, this::startLoginInThread);
                        return;
                    }
                    commandScheduler.supply(this::tryRestoreFromCachedRefreshToken)
                            .thenAccept(restored -> {
                                if (restored)
                                {
                                    getLogger().info("startup: silent reauth succeeded — main view loading, no dialog");
                                    SwingSafe.invokeLater(logger, this::beginRaplaSessionAfterRestore);
                                }
                                else
                                {
                                    getLogger().info("startup: silent reauth did not restore — falling through to login dialog");
                                    SwingSafe.invokeLater(logger, this::startLoginInThread);
                                }
                            })
                            .exceptionally(ex -> {
                                getLogger().info("startup: silent reauth failed (" + ex.getMessage() + ") — falling through to login dialog");
                                SwingSafe.invokeLater(logger, this::startLoginInThread);
                            });
                })
                .exceptionally(ex -> {
                    getLogger().info("startup: discovery failed (" + ex.getMessage() + ") — showing login dialog");
                    SwingSafe.invokeLater(logger, this::startLoginInThread);
                });
    }

    /**
     * Reads the cached refresh token (if any), calls the refresh endpoint to mint
     * a fresh access token, and stashes both on the connectionInfo. Returns true
     * if the user is now logged in; false to fall through to the login dialog.
     * Never throws — any failure falls through.
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
            getLogger().info("startup: token-store read failed: " + t + " — falling back to login dialog");
            return false;
        }
        if (cached.isEmpty())
        {
            getLogger().info("startup: no cached refresh token (first launch or after logout) — login dialog expected");
            return false;
        }
        String cachedRefresh = cached.get();
        getLogger().info("startup: cached refresh token found — attempting silent reauth");
        try
        {
            // The discovery refresh URL isn't known until we hit /api/auth/oauth/config —
            // for the cached-token path we use the rapla default <server>/api/auth/refresh.
            // If discovery later changes the refresh URL (Keycloak), the cached token
            // from the embedded auth server won't validate there anyway — fall through.
            String serverUrl = connectionInfo.getServerURL();
            if (serverUrl == null || serverUrl.isEmpty())
            {
                getLogger().info("startup: server URL not yet set — falling back to login dialog");
                return false;
            }
            String refreshEndpoint = serverUrl + "/api/auth/refresh";
            String body = "{\"refreshToken\":\"" + cachedRefresh + "\"}";
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10)).build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(refreshEndpoint))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            java.net.http.HttpResponse<String> resp = http.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2)
            {
                getLogger().info("startup: cached refresh token rejected by server (HTTP " + resp.statusCode()
                        + ") — clearing cache and falling back to login dialog");
                tokenStore.tryClear();
                return false;
            }
            String respBody = resp.body();
            String newAccess = extractJson(respBody, "accessToken");
            String newRefresh = extractJson(respBody, "refreshToken");
            if (newAccess == null)
            {
                getLogger().info("startup: refresh response missing accessToken — falling back to login dialog. body=" + respBody);
                return false;
            }
            ConnectInfo info = ConnectInfo.withAccessToken(newAccess, newRefresh);
            reconnectInfo = info;
            connectionInfo.setAccessToken(newAccess);
            if (newRefresh != null)
            {
                connectionInfo.setRefreshToken(newRefresh);
                tokenStore.tryWrite(newRefresh);
            }
            connectionInfo.setReconnectInfo(info);
            getLogger().info("startup: silent reauth via cached refresh token succeeded — skipping login dialog");
            return true;
        }
        catch (Throwable t)
        {
            getLogger().info("startup: refresh HTTP call failed: " + t + " — falling back to login dialog");
            return false;
        }
    }

    /** Appends {@code id_token_hint=<idToken>} to the OIDC end-session URL.
     *  When {@code idToken} is null/blank, returns the URL unchanged — the server
     *  may reject the logout, but at least we don't send a malformed URL. */
    private static String appendIdTokenHint(String logoutUrl, String idToken)
    {
        if (idToken == null || idToken.isEmpty()) return logoutUrl;
        String encoded = java.net.URLEncoder.encode(idToken, java.nio.charset.StandardCharsets.UTF_8);
        char sep = logoutUrl.indexOf('?') < 0 ? '?' : '&';
        return logoutUrl + sep + "id_token_hint=" + encoded;
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

    private void beginRaplaSessionAfterRestore()
    {
        beginRaplaSession().exceptionally(ex -> {
            getLogger().error("post-restore session start failed; falling back to login dialog", ex);
            // Drop the cached token if the session can't start with it for any reason
            tokenStore.tryClear();
            SwingSafe.invokeLater(logger, this::startLoginInThread);
        });
    }

    private void startLoginInThread()
    {
        final Semaphore loginMutex = new Semaphore(1);
        try
        {
            final Logger logger = getLogger();
            final AbstractBundleManager localeSelector = (AbstractBundleManager) bundleManager;
            // PRD 029 Phase 4: restore the language used at the last successful
            // login (TokenStore pref) before building the dialog, so it renders
            // in that language straight away.
            final String savedLanguage = readLoginPref(TokenStore.KEY_LANGUAGE);
            if (!savedLanguage.isEmpty())
            {
                try { localeSelector.setLanguage(savedLanguage); }
                catch (Exception ex) { getLogger().debug("could not restore saved language '" + savedLanguage + "': " + ex); }
            }
            final LanguageChooser languageChooser = new LanguageChooser(logger, i18n, raplaLocale);
            activeLanguageChooser = languageChooser;
            final LoginDialog dlg = LoginDialog.create(env, i18n, localeSelector, logger, raplaLocale, languageChooser.getComponent());
            // Holds the OAuth providers offered in the method dropdown, in
            // dropdown order (entry 0 = Password is not in this list), so the
            // Login button's action can resolve the picked provider (PRD 029
            // Phase 4).
            final java.util.concurrent.atomic.AtomicReference<java.util.List<OAuthConfig>> dropdownProviders =
                    new java.util.concurrent.atomic.AtomicReference<>(java.util.List.of());

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
                            getLogger().debug("Language changing to " + lang);
                            localeSelector.setLanguage(lang);
                            getLogger().info("Language changed " + localeSelector.getLocale().getLanguage());
                        }
                    }
                    catch (Exception ex)
                    {
                        getLogger().error("Can't change language", ex);
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
                    // PRD 029 Phase 4: the Login button is method-aware. Index 0
                    // is the local username/password grant; any other index is a
                    // browser-based OAuth provider from discovery.
                    int methodIndex = dlg.getSelectedMethodIndex();
                    if (methodIndex > 0)
                    {
                        java.util.List<OAuthConfig> providers = dropdownProviders.get();
                        if (methodIndex - 1 < providers.size())
                        {
                            runOauthLogin(dlg, loginMutex, providers.get(methodIndex - 1));
                        }
                        return;
                    }
                    String username = dlg.getUsername();
                    char[] password = dlg.getPassword();
                    final String[] split = username.split(" su ");
                    String connectAs = null;
                    if (split.length > 1) {
                        username = split[0];
                        connectAs = split[1];
                    }
                    reconnectInfo = new ConnectInfo(username, password, connectAs);
                    dlg.busy( i18n.getString("login"));
                    login(reconnectInfo).thenAccept(
                            (success) ->
                    {
                        if (!success)
                        {
                            dlg.resetPassword();
                            dlg.idle();
                            dialogUiFactory.showWarning(i18n.getString("error.login"), new SwingPopupContext(dlg, null));
                        }
                        else
                        {
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
                            }
                            );
                        }
                    }).exceptionally((ex)->
                    {
                        dlg.resetPassword();
                        dialogUiFactory.showException(ex, new SwingPopupContext(dlg, null));
                        dlg.idle();
                    });

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
                }
            };
            loginAction.putValue(Action.NAME, i18n.getString("login"));
            exitAction.putValue(Action.NAME, i18n.getString("exit"));
            dlg.setIconImage(RaplaImages.getImage(i18n.getIcon("icon.rapla_small")));
            dlg.setLoginAction(loginAction);
            dlg.setExitAction(exitAction);
            centerWindowOnScreen(dlg);

            // PRD 029 Phase 2/3/4: OAuth is the primary login path by default.
            // When discovery says OAuth is enabled AND the admin has not opted
            // into the legacy dialog (rapla.oauth.swing-legacy-login), show the
            // dialog in "browser login in progress" mode and auto-launch the
            // browser flow against the rapla SAS.
            // Otherwise the legacy dialog is shown. When the admin also set
            // rapla.oauth.swing-legacy-show-sso-button, the dialog offers a
            // sign-in-method dropdown (Password + every discovery provider —
            // rapla SAS, Keycloak, …); picking a browser provider greys out the
            // username/password fields. Without that flag, or when OAuth is
            // unavailable, only the plain password form is shown.
            commandScheduler.supply(this::fetchOauthConfig).thenAccept(cfg -> SwingSafe.invokeLater(logger, () -> {
                boolean oauthEnabled = cfg != null && cfg.isEnabled();
                boolean legacyLogin = cfg != null && cfg.isSwingLegacyLogin();
                if (oauthEnabled && !legacyLogin)
                {
                    getLogger().info("startup: discovery confirms OAuth enabled — auto-launching browser flow (Swing dialog stays in waiting mode)");
                    dlg.setBrowserLoginInProgress(i18n.getString("login.oauth.waiting"));
                    dlg.setVisible(true);
                    runOauthLogin(dlg, loginMutex, cfg);
                }
                else
                {
                    boolean showProviders = oauthEnabled && legacyLogin && cfg.isSwingLegacyShowSsoButton();
                    java.util.List<OAuthConfig> methodProviders = configureLoginMethods(dlg, showProviders ? cfg : null);
                    dropdownProviders.set(methodProviders);
                    // PRD 029 Phase 4: pre-select the method used at the last login.
                    applySavedLoginMethod(dlg, methodProviders);
                    getLogger().info("startup: showing legacy Swing login dialog"
                            + (oauthEnabled ? " (admin set rapla.oauth.swing-legacy-login)" : " (OAuth disabled server-side)")
                            + (showProviders ? " with the sign-in-method dropdown" : ""));
                    dlg.setVisible(true);
                }
            })).exceptionally(ex -> SwingSafe.invokeLater(logger, () -> {
                Throwable root = ex;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                getLogger().info("startup: discovery failed (" + root.getClass().getSimpleName() + ": " + root.getMessage() + ") — showing legacy Swing login dialog as fallback");
                // Discovery failed — OAuth support can't be confirmed, so offer
                // only the local password method.
                dropdownProviders.set(configureLoginMethods(dlg, null));
                dlg.setVisible(true);
            }));

            loginMutex.acquire();
        }
        catch (Exception ex)
        {
            getLogger().error("Error during Login ", ex);
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
            getLogger().debug("could not persist login prefs: " + t);
        }
    }

    /** Pre-selects the method dropdown to the provider used at the last login.
     *  No-op for "password" / unknown / absent (index 0 is the default). */
    private void applySavedLoginMethod(LoginDialog dlg, java.util.List<OAuthConfig> providers)
    {
        String saved = readLoginPref(TokenStore.KEY_LOGIN_METHOD);
        if (saved.isEmpty() || "password".equals(saved)) return;
        for (int i = 0; i < providers.size(); i++)
        {
            if (saved.equals(providers.get(i).getId()))
            {
                dlg.setSelectedMethodIndex(i + 1);
                return;
            }
        }
    }

    // PRD 029 Phase 4 — providers that can complete the desktop loopback PKCE
    // flow today. The rapla SAS and Keycloak are public PKCE clients with a
    // loopback redirect registered. Microsoft (Entra SPA-platform) and Google
    // (BFF/Web-app) can't reuse their SPA discovery entries — bringing them to
    // Swing needs separate native-app registrations; deferred.
    private static final java.util.Set<String> SWING_OAUTH_PROVIDERS = java.util.Set.of("rapla", "keycloak");

    /** Populates the login-method dropdown: index 0 is the local username/password
     *  grant, the rest are the Swing-capable discovery providers (rapla SAS,
     *  Keycloak). When {@code cfg} is null only the password method is offered
     *  (no dropdown). Returns the providers behind dropdown indices 1..N, in
     *  order, so the Login button can resolve the selection. PRD 029 Phase 4. */
    private java.util.List<OAuthConfig> configureLoginMethods(LoginDialog dlg, OAuthConfig cfg)
    {
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<OAuthConfig> usable = new java.util.ArrayList<>();
        labels.add(i18n.getString("password"));
        if (cfg != null)
        {
            for (OAuthConfig provider : cfg.getProviders())
            {
                if (provider.getId() != null && SWING_OAUTH_PROVIDERS.contains(provider.getId()))
                {
                    labels.add(providerLabel(provider));
                    usable.add(provider);
                }
            }
        }
        dlg.setLoginMethods(labels);
        // Greying the username/password fields whenever a browser provider is
        // picked makes it obvious they don't apply to that method.
        dlg.setMethodChangeListener(e -> dlg.setCredentialsEnabled(dlg.getSelectedMethodIndex() == 0));
        return usable;
    }

    private static String providerLabel(OAuthConfig provider)
    {
        String id = provider.getId();
        if (id != null && !id.isEmpty())
        {
            return Character.toUpperCase(id.charAt(0)) + id.substring(1);
        }
        return provider.getDisplayName() != null ? provider.getDisplayName() : "OAuth";
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
                getLogger().info("OAuth login: user clicked Abort — cancelling the browser-login wait");
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
            getLogger().info("OAuth login: starting browser flow for provider '"
                    + (provider.getId() != null ? provider.getId() : "rapla") + "'");
            if (provider.getLogoutUrl() != null)
            {
                connectionInfo.setLogoutUrl(provider.getLogoutUrl());
            }
            // PRD 029 Phase 4: remember this provider's token endpoint + client_id
            // so MyCustomConnector refreshes against the right place — the BFF
            // (/api/auth/oauth/exchange/{id}) for a secret-backed provider like
            // Keycloak, the rapla SAS /oauth2/token for the rapla provider.
            connectionInfo.setRefreshUrl(provider.getTokenUrl());
            connectionInfo.setOauthClientId(provider.getClientId());
            SwingOAuthLoginFlow flow = new SwingOAuthLoginFlow(provider, getLogger());
            boolean force = nextOauthForcesLogin;
            nextOauthForcesLogin = false;
            if (force)
            {
                flow.forceLogin(true);
                getLogger().info("OAuth flow: forcing IdP login (prompt=login) — post-logout restart");
            }
            SwingOAuthLoginFlow.Session session = flow.start();
            sessionRef.set(session);
            if (aborted.get())
            {
                // User clicked Abort during the discovery probe, before the
                // session existed — honour it now.
                session.future().cancel(true);
            }
            if (provider.isShowPasteFallback())
            {
                scheduleDelayedPasteHelper(dlg, session);
            }
            return session.future().get();
        }).thenAccept(tokens -> SwingSafe.invokeLater(logger, () -> finishOauthLogin(dlg, loginMutex, tokens, provider)))
                .exceptionally(ex -> SwingSafe.invokeLater(logger, () -> {
                    Throwable root = unwrap(ex);
                    if (root instanceof java.util.concurrent.CancellationException)
                    {
                        getLogger().info("OAuth login cancelled — restoring Swing login dialog to full state");
                        dlg.idle();
                        dlg.clearBrowserLoginInProgress();
                        if (!dlg.isVisible()) dlg.setVisible(true);
                        return;
                    }
                    getLogger().error("OAuth login failed — restoring Swing login dialog to full state", ex);
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

    private void scheduleDelayedPasteHelper(LoginDialog dlg, SwingOAuthLoginFlow.Session session)
    {
        // Don't show the paste dialog if the automatic callback arrives quickly
        // (the typical case on native OSes). Wait 12 s; if the flow hasn't
        // completed by then, surface the fallback so the user can paste the URL
        // their browser is stuck on.
        java.util.concurrent.CompletableFuture
                .runAsync(() -> {}, java.util.concurrent.CompletableFuture.delayedExecutor(12, java.util.concurrent.TimeUnit.SECONDS))
                .thenRun(() -> SwingSafe.invokeLater(logger, () -> {
                    if (session.future().isDone())
                    {
                        return;
                    }
                    javax.swing.JDialog paste = OAuthCallbackPasteDialog.show(dlg, i18n, getLogger(),
                            pastedUrl -> {
                                try
                                {
                                    session.deliverPasted(pastedUrl);
                                }
                                catch (Exception ex)
                                {
                                    getLogger().error("paste delivery failed", ex);
                                    dialogUiFactory.showException(ex, new SwingPopupContext(dlg, null));
                                }
                            },
                            () -> {
                                // User cancelled the paste dialog — abort the OAuth flow so the
                                // login dialog releases its busy state and the user can retry
                                // without waiting the 5-minute callback timeout.
                                session.future().cancel(true);
                            });
                    session.future().whenComplete((t, e) -> SwingSafe.invokeLater(logger, paste::dispose));
                }));
    }

    private void finishOauthLogin(LoginDialog dlg, Semaphore loginMutex, OAuthTokens tokens, OAuthConfig provider)
    {
        ConnectInfo info = ConnectInfo.withAccessToken(tokens.getAccessToken(), tokens.getRefreshToken());
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
            // PRD 029 Phase 4: remember the language + provider for next launch.
            persistLoginPrefs(provider != null && provider.getId() != null ? provider.getId() : "rapla");
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
        boolean showPasteFallback = tree.path("showPasteFallback").asBoolean(false);
        // PRD 029 Phase 4: parse the providers[] array so the Swing login dialog
        // can offer a method dropdown (rapla SAS, Keycloak, …). Each entry is
        // turned into a provider-level OAuthConfig that SwingOAuthLoginFlow can
        // consume directly. Absent on older servers → empty list, no dropdown.
        List<OAuthConfig> providers = new java.util.ArrayList<>();
        if (tree.has("providers") && tree.get("providers").isArray())
        {
            for (tools.jackson.databind.JsonNode p : tree.get("providers"))
            {
                List<String> pScopes = new java.util.ArrayList<>();
                if (p.has("scopes") && p.get("scopes").isArray())
                {
                    p.get("scopes").forEach(n -> pScopes.add(n.asString()));
                }
                String pEndSession = p.has("endSessionUrl") && !p.get("endSessionUrl").isNull()
                        ? p.get("endSessionUrl").asString() : null;
                providers.add(new OAuthConfig(
                        true,
                        p.path("clientId").asString(),
                        p.path("authorizeUrl").asString(),
                        p.path("tokenUrl").asString(),
                        pEndSession,
                        pScopes,
                        showPasteFallback,
                        false,
                        false,
                        p.path("id").asString(),
                        p.path("displayName").asString(),
                        List.of()));
            }
        }
        return new OAuthConfig(
                true,
                tree.path("clientId").asString(),
                tree.path("authorizeUrl").asString(),
                tree.path("tokenUrl").asString(),
                logoutUrl,
                scopes,
                showPasteFallback,
                swingLegacyLogin,
                swingLegacyShowSsoButton,
                "rapla",
                null,
                providers);
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
        getLogger().error("Error updating data", ex);
    }

    public void disconnected(final String message)
    {
        if (schedule != null) {
            schedule.cancel();
        }
        this.schedule = null;
        if (started)
        {
            SwingSafe.invokeLater(logger, () -> {
                boolean modal = false;
                String title = i18n.getString("restart_client");
                try
                {
                    Component owner = null;
                    final DialogInterface dialog = dialogUiFactory.createInfoDialog(new SwingPopupContext(owner, null), title, message);
                    dialog.setCloseAction(()->
                    {
                        getLogger().warn("restart");
                        dialog.close();
                        restart();
                    }
                    );
                    dialog.start(true);
                }
                catch (Throwable e)
                {
                    getLogger().error(e.getMessage(), e);
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
                    getLogger().info("logout: server-side session revoked");
                }
                else
                {
                    getLogger().info("logout: server returned HTTP " + resp.statusCode() + "; local logout proceeds");
                }
            }
            catch (Throwable t)
            {
                getLogger().info("logout: server-side revocation failed (" + t.getMessage() + "); local logout proceeds");
            }
        }
        // Also clear the browser's session AND remember-me cookies at the auth
        // server. Discovery's logoutUrl points at /connect/logout (OIDC RP-initiated
        // logout). That endpoint requires an id_token_hint per spec — without it
        // Spring SAS returns 404 and the rapla-remember-me cookie survives. With
        // a valid hint, Spring's success handler (wired by AuthorizationServerConfig)
        // also runs the CompositeLogoutHandler that drops the remember-me cookie.
        String logoutUrl = connectionInfo.getLogoutUrl();
        if (logoutUrl != null && !logoutUrl.isEmpty())
        {
            try
            {
                String fullUrl = appendIdTokenHint(logoutUrl, connectionInfo.getIdToken());
                org.rapla.client.internal.BrowserLauncher.open(URI.create(fullUrl), getLogger());
                getLogger().info("logout: opened browser to clear IdP session cookie at " + logoutUrl
                        + (connectionInfo.getIdToken() != null ? " (with id_token_hint)" : " (NO id_token — server may reject)"));
            }
            catch (Throwable t)
            {
                getLogger().info("logout: couldn't open browser for IdP logout (" + t.getMessage() + "); local logout proceeds anyway");
            }
        }
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
        // Tell the next OAuth flow to force the IdP login form regardless of
        // the browser's session cookie. The cookie SHOULD be cleared by the
        // logoutUrl tab opened above, but there's a race: that tab may not
        // have completed before /oauth2/authorize runs. prompt=login defeats
        // the race deterministically.
        nextOauthForcesLogin = true;
        stop(new ConnectInfo(null, "".toCharArray()));
        // PRD 052 Phase 2 — signal SpringRaplaClient.main() to close this
        // Spring context and build a fresh one for the next login. main()
        // disposes JFrames, runs ctx.close() (which fires DisposableBean /
        // @PreDestroy across every bean), and rebuilds the context. The user
        // sees a fresh login dialog in a fresh context with zero carry-over
        // of cached entities, UI state, or singleton listener registrations.
        logoutSignal.next(org.rapla.client.spring.NextSession.showLoginDialog());
    }

    private Promise<Boolean> login(ConnectInfo connectInfo)
    {
        if (connectInfo.getAccessToken() != null)
        {
            return commandScheduler.supply(() -> {
                this.connectionInfo.setAccessToken(connectInfo.getAccessToken());
                this.connectionInfo.setRefreshToken(connectInfo.getRefreshToken());
                this.connectionInfo.setReconnectInfo(connectInfo);
                this.reconnectInfo = connectInfo;
                if (connectInfo.getRefreshToken() != null)
                {
                    tokenStore.tryWrite(connectInfo.getRefreshToken());
                    getLogger().info("login: refresh token persisted for next launch (skip login dialog)");
                }
                else
                {
                    getLogger().warn("login: no refresh token received — next launch will require login again");
                }
                return true;
            });
        }
        String connectAs = connectInfo.getConnectAs();
        String password = new String(connectInfo.getPassword());
        String username = connectInfo.getUsername();
        return commandScheduler.supply(()->
        {
            LoginTokens loginToken;
            try {
                loginToken = authentificationService.login(new org.rapla.storage.dbrm.LoginCredentials(username, password, connectAs));
            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized ex) {
                return false;
            }
            String accessToken = loginToken.getAccessToken();
            if (accessToken != null) {
                this.connectionInfo.setAccessToken(accessToken);
                this.connectionInfo.setRefreshToken(loginToken.getRefreshToken());
                this.connectionInfo.setReconnectInfo( connectInfo);
                this.reconnectInfo = connectInfo;
                if (loginToken.getRefreshToken() != null)
                {
                    tokenStore.tryWrite(loginToken.getRefreshToken());
                    getLogger().info("login: refresh token persisted for next launch (skip login dialog)");
                }
                else
                {
                    getLogger().warn("login: no refresh token from /auth/login — next launch will require login again");
                }
            } else {
                throw new RaplaSecurityException("Invalid Access token");
            }
            return true;
        });
    }

    public boolean isLogoutAvailable()
    {
        return logoutAvailable;
    }

}
