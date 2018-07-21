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
import org.rapla.client.ClientService;
import org.rapla.client.RaplaClientListener;
import org.rapla.client.UserClientService;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.LanguageChooser;
import org.rapla.client.internal.LoginDialog;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingSchedulerImpl;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.client.swing.SwingBundleManager;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.UpdateErrorListener;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.ClientFacadeImpl;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteAuthentificationService;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteOperator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.Semaphore;

/** Implementation of the UserClientService.
*/
@Singleton
@DefaultImplementation(of = ClientService.class, context = InjectionContext.swing, export = true)
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
    io.reactivex.disposables.Disposable schedule;

    Application application;
    final private Provider<Application> applicationProvider;
    RemoteAuthentificationService authentificationService;
    RemoteConnectionInfo connectionInfo;

    @Inject
    public RaplaClientServiceImpl(StartupEnvironment env, Logger logger, DialogUiFactoryInterface dialogUiFactory, ClientFacade facade, RaplaResources i18n, RaplaSystemInfo systemInfo,
                                  RaplaLocale raplaLocale, BundleManager bundleManager, CommandScheduler commandScheduler, final RemoteOperator storageOperator,
                                  Provider<Application> applicationProvider, RemoteConnectionInfo connectionInfo, RemoteAuthentificationService authentificationService)
    {
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
            connectionInfo.setServerURL(downloadURL.toExternalForm() + "rapla");
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
        if (startupMode != StartupEnvironment.APPLET && startupMode != StartupEnvironment.WEBSTART)
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
                    ((SwingBundleManager) bundleManager).setLanguage(language);
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
        SwingUtilities.invokeLater(()->startLoginInThread());
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
                    String connectAs = null;
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
            loginAction.putValue(Action.NAME, i18n.getString("login"));
            exitAction.putValue(Action.NAME, i18n.getString("exit"));
            dlg.setIconImage(RaplaImages.getImage(i18n.getIcon("icon.rapla_small")));
            dlg.setLoginAction(loginAction);
            dlg.setExitAction(exitAction);
            //dlg.setSize( 480, 270);
            centerWindowOnScreen(dlg);
            dlg.setVisible(true);

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
            schedule.dispose();
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
                    dialog.setAbortAction(()->
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
        if (reconnectInfo != null)
        {
            stop(reconnectInfo);
        }
    }

    public void logout()
    {
        stop(new ConnectInfo(null, "".toCharArray()));
    }

    private Promise<Boolean> login(ConnectInfo connectInfo)
    {
        String connectAs = connectInfo.getConnectAs();
        String password = new String(connectInfo.getPassword());
        String username = connectInfo.getUsername();
        return commandScheduler.supply(()->
        {
            LoginTokens loginToken = authentificationService.login(username, password, connectAs);
            String accessToken = loginToken.getAccessToken();
            if (accessToken != null) {
                this.connectionInfo.setAccessToken(accessToken);
                this.connectionInfo.setReconnectInfo( connectInfo);
                this.reconnectInfo = connectInfo;
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
