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
import org.rapla.storage.dbrm.RemoteAuthentificationService;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteOperator;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
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
    /** Set by logout(); read+cleared by the next runOauthLogin call. Adds
     *  prompt=login to the authorize URL to defeat the cookie-reuse race
     *  when logout-and-restart happen in quick succession in the same JVM. */
    private volatile boolean nextOauthForcesLogin = false;

    @Autowired
    public RaplaClientServiceImpl(StartupEnvironment env, Logger logger, DialogUiFactoryInterface dialogUiFactory, ClientFacade facade, RaplaResources i18n, RaplaSystemInfo systemInfo,
                                  RaplaLocale raplaLocale, BundleManager bundleManager, CommandScheduler commandScheduler, final RemoteOperator storageOperator,
                                  Supplier<Application> applicationProvider, RemoteConnectionInfo connectionInfo, RemoteAuthentificationService authentificationService,
                                  org.rapla.storage.dbrm.TokenStore tokenStore)
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

        // EDT exceptions bypass Thread.setDefaultUncaughtExceptionHandler — they go through
        // EventDispatchThread.processException, which only consults the legacy sun.awt.exception.handler
        // system property and otherwise prints to System.err. Wrap the system event queue so every
        // dispatched AWT event runs inside our try/catch and uncaughts surface through the rapla logger.
        try
        {
            Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue()
            {
                @Override
                protected void dispatchEvent(AWTEvent event)
                {
                    try
                    {
                        super.dispatchEvent(event);
                    }
                    catch (Throwable t)
                    {
                        logger.error("Uncaught exception in AWT event dispatch", t);
                    }
                }
            });
        }
        catch (Throwable ex)
        {
            logger.error("Can't install AWT event-queue exception handler", ex);
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
        advanceLoading(true);

        logoutAvailable = true;
        if (connectInfo != null && connectInfo.getUsername() != null)
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

    public void switchTo(User user) throws RaplaException
    {
        ClientFacade facade = getClientFacade();
        if (user == null)
        {
            if (reconnectInfo == null || reconnectInfo.getConnectAs() == null)
            {
                throw new RaplaException("Can't switch back because there were no previous logins.");
            }
            final String oldUser = facade.getUser().getUsername();
            String newUser = reconnectInfo.getUsername();
            char[] password = reconnectInfo.getPassword();
            getLogger().info("Login From:" + oldUser + " To:" + newUser);
            ConnectInfo reconnectInfo = new ConnectInfo(newUser, password);
            stop(reconnectInfo);
        }
        else
        {
            if (reconnectInfo == null)
            {
                throw new RaplaException("Can't switch to user, because admin login information not provided due missing login.");

            }
            if (reconnectInfo.getConnectAs() != null)
            {
                throw new RaplaException("Can't switch to user, because already switched.");
            }
            final String oldUser = reconnectInfo.getUsername();
            final String newUser = user.getUsername();
            getLogger().info("Login From:" + oldUser + " To:" + newUser);
            ConnectInfo newInfo = new ConnectInfo(oldUser, reconnectInfo.getPassword(), newUser);
            stop(newInfo);
        }
        // fireUpdateEvent(new ModificationEvent());
    }

    public boolean canSwitchBack()
    {
        return reconnectInfo != null && reconnectInfo.getConnectAs() != null;
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
        // Try to skip the login dialog entirely by refreshing a cached refresh token.
        // PRD 029 Phase 2: zero-browser-on-launch property. Best-effort — any failure
        // (no cached token, token expired, server unreachable, store unreadable) just
        // falls through to the normal login dialog. Runs on a worker thread so the
        // EDT isn't blocked by the HTTP call.
        getLogger().info("startup: checking for cached refresh token to skip the login dialog…");
        commandScheduler.supply(this::tryRestoreFromCachedRefreshToken)
                .thenAccept(restored -> {
                    if (restored)
                    {
                        getLogger().info("startup: silent reauth succeeded — main view loading, no dialog");
                        SwingUtilities.invokeLater(this::beginRaplaSessionAfterRestore);
                    }
                    else
                    {
                        getLogger().info("startup: falling back to login dialog (Swing dialog is the emergency fallback path)");
                        SwingUtilities.invokeLater(this::startLoginInThread);
                    }
                })
                .exceptionally(ex -> {
                    getLogger().info("startup: restore failed (" + ex.getMessage() + ") — falling back to login dialog");
                    SwingUtilities.invokeLater(this::startLoginInThread);
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
            SwingUtilities.invokeLater(this::startLoginInThread);
        });
    }

    private void startLoginInThread()
    {
        final Semaphore loginMutex = new Semaphore(1);
        try
        {
            final Logger logger = getLogger();
            final LanguageChooser languageChooser = new LanguageChooser(logger, i18n, raplaLocale);
            final AbstractBundleManager localeSelector = (AbstractBundleManager) bundleManager;
            final LoginDialog dlg = LoginDialog.create(env, i18n, localeSelector, logger, raplaLocale, languageChooser.getComponent());

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
            //dlg.setIcon( i18n.getIcon("icon.rapla-small"));
            Action loginAction = new AbstractAction()
            {
                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent evt)
                {
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
            Action oauthAction = new AbstractAction()
            {
                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent evt)
                {
                    runOauthLogin(dlg, loginMutex);
                }
            };
            loginAction.putValue(Action.NAME, i18n.getString("login"));
            exitAction.putValue(Action.NAME, i18n.getString("exit"));
            oauthAction.putValue(Action.NAME, i18n.getString("login.oauth.button"));
            dlg.setIconImage(RaplaImages.getImage(i18n.getIcon("icon.rapla_small")));
            dlg.setLoginAction(loginAction);
            dlg.setExitAction(exitAction);
            dlg.setOauthAction(oauthAction);
            centerWindowOnScreen(dlg);

            // PRD 029 Phase 2: OAuth is the primary login path. If discovery
            // says OAuth is enabled, show the dialog in "browser login in
            // progress" mode (status message, no credential fields, Exit
            // still enabled to abort) and auto-launch the browser flow.
            // If OAuth is disabled OR the probe fails, show the dialog in
            // its normal full state as a fallback.
            commandScheduler.supply(this::fetchOauthConfig).thenAccept(cfg -> SwingUtilities.invokeLater(() -> {
                if (cfg != null && cfg.isEnabled())
                {
                    getLogger().info("startup: discovery confirms OAuth enabled — auto-launching browser flow (Swing dialog stays in waiting mode)");
                    dlg.setBrowserLoginInProgress(i18n.getString("login.oauth.waiting"));
                    dlg.setVisible(true);
                    oauthAction.actionPerformed(null);
                }
                else
                {
                    getLogger().info("startup: OAuth not enabled — showing Swing login dialog as fallback");
                    dlg.setVisible(true);
                }
            })).exceptionally(ex -> SwingUtilities.invokeLater(() -> {
                Throwable root = ex;
                while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                getLogger().info("startup: discovery failed (" + root.getClass().getSimpleName() + ": " + root.getMessage() + ") — showing Swing login dialog as fallback");
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

    private void runOauthLogin(LoginDialog dlg, Semaphore loginMutex)
    {
        dlg.busy(i18n.getString("login.oauth.button"));
        commandScheduler.supply(() -> {
            OAuthConfig cfg = fetchOauthConfig();
            if (cfg == null || !cfg.isEnabled())
            {
                throw new IllegalStateException("OAuth login not enabled on the server");
            }
            if (cfg.getRefreshUrl() != null)
            {
                connectionInfo.setRefreshUrl(cfg.getRefreshUrl());
            }
            if (cfg.getLogoutUrl() != null)
            {
                connectionInfo.setLogoutUrl(cfg.getLogoutUrl());
            }
            SwingOAuthLoginFlow flow = new SwingOAuthLoginFlow(cfg, getLogger());
            boolean force = nextOauthForcesLogin;
            nextOauthForcesLogin = false;
            if (force)
            {
                flow.forceLogin(true);
                getLogger().info("OAuth flow: forcing IdP login (prompt=login) — post-logout restart");
            }
            SwingOAuthLoginFlow.Session session = flow.start();
            if (cfg.isShowPasteFallback())
            {
                scheduleDelayedPasteHelper(dlg, session);
            }
            return session.future().get();
        }).thenAccept(tokens -> SwingUtilities.invokeLater(() -> finishOauthLogin(dlg, loginMutex, tokens)))
                .exceptionally(ex -> SwingUtilities.invokeLater(() -> {
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
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
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
                    session.future().whenComplete((t, e) -> SwingUtilities.invokeLater(paste::dispose));
                }));
    }

    private void finishOauthLogin(LoginDialog dlg, Semaphore loginMutex, OAuthTokens tokens)
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
        String refreshUrl = tree.has("refreshUrl") && !tree.get("refreshUrl").isNull()
                ? tree.get("refreshUrl").asString() : null;
        String logoutUrl = tree.has("logoutUrl") && !tree.get("logoutUrl").isNull()
                ? tree.get("logoutUrl").asString() : null;
        return new OAuthConfig(
                true,
                tree.path("clientId").asString(),
                tree.path("authorizeUrl").asString(),
                tree.path("tokenUrl").asString(),
                refreshUrl,
                logoutUrl,
                scopes,
                tree.path("showPasteFallback").asBoolean(false));
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
            SwingUtilities.invokeLater(() -> {
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
        String accessToken = connectionInfo.getAccessToken();
        String serverUrl = connectionInfo.getServerURL();
        if (accessToken != null && serverUrl != null && !serverUrl.isEmpty())
        {
            try
            {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(serverUrl + "/api/auth/logout"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Authorization", "Bearer " + accessToken)
                        .POST(HttpRequest.BodyPublishers.noBody())
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
        // Tell the next OAuth flow to force the IdP login form regardless of
        // the browser's session cookie. The cookie SHOULD be cleared by the
        // logoutUrl tab opened above, but there's a race: that tab may not
        // have completed before /oauth2/authorize runs. prompt=login defeats
        // the race deterministically.
        nextOauthForcesLogin = true;
        stop(new ConnectInfo(null, "".toCharArray()));
        // After logout, re-enter the login flow in the same JVM so the user
        // sees the login dialog / browser flow without needing to relaunch.
        // start() guards on `started == false` (which stop() just set), so this
        // is safe.
        SwingUtilities.invokeLater(() -> {
            try
            {
                this.start(null);
            }
            catch (Exception ex)
            {
                getLogger().error("Failed to restart login flow after logout", ex);
            }
        });
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
