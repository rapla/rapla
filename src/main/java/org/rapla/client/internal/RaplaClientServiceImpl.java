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
package org.rapla.client.internal;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.client.ClientService;
import org.rapla.client.RaplaClientListener;
import org.rapla.client.UserClientService;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ClientExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.MainFrame;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.UpdateErrorListener;
import org.rapla.facade.UserModule;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteOperator;
import org.rapla.storage.dbrm.StatusUpdater;

/** Implementation of the UserClientService.
*/
@Singleton
@DefaultImplementation(of=ClientService.class,context = InjectionContext.swing,export = true)
public class RaplaClientServiceImpl implements ClientService,UpdateErrorListener,Disposable,UserClientService
{
    
    Vector<RaplaClientListener> listenerList = new Vector<RaplaClientListener>();
    RaplaResources i18n;
    boolean started;
    boolean restartingGUI;
    boolean defaultLanguageChoosen;
    private FrameControllerList frameControllerList;
    boolean logoutAvailable;
    ConnectInfo reconnectInfo;
	static boolean lookAndFeelSet;
    final Logger logger;
    final StartupEnvironment env;
    final DialogUiFactoryInterface dialogUiFactory;
    final ClientFacade facade;
    final RaplaLocale raplaLocale;
    final BundleManager bundleManager;
    final CommandScheduler commandScheduler;
    final RaplaImages raplaImages;
    final Provider<MainFrame> mainFrameProvider;
    final Provider<RaplaFrame> raplaFrameProvider;
    final private Provider<CalendarSelectionModel> calendarModel;
    final private Provider<Set<ClientExtension>> clientExtensions;
    @Inject
	public RaplaClientServiceImpl(StartupEnvironment env, Logger logger, DialogUiFactoryInterface dialogUiFactory, ClientFacade facade, RaplaResources i18n,
            FrameControllerList frameControllerList, RaplaLocale raplaLocale, BundleManager bundleManager, CommandScheduler commandScheduler, final StorageOperator storageOperator,
            RaplaImages raplaImages, Provider<MainFrame> mainFrameProvider, Provider<RaplaFrame> raplaFrameProvider, Provider<CalendarSelectionModel> calendarModel,
            Provider<Set<ClientExtension>> clientExtensions) {
        this.env = env;
        this.i18n = i18n;
        this.logger = logger;
        this.dialogUiFactory = dialogUiFactory;
        this.facade = facade;
        this.frameControllerList = frameControllerList;
        this.raplaLocale = raplaLocale;
        this.bundleManager = bundleManager;
        this.commandScheduler = commandScheduler;
        this.mainFrameProvider = mainFrameProvider;
        this.raplaFrameProvider = raplaFrameProvider;
        this.calendarModel = calendarModel;
        this.clientExtensions = clientExtensions;
        ((FacadeImpl)this.facade).setOperator( storageOperator);
        this.raplaImages = raplaImages;
        initialize();
    }


    public Logger getLogger()
    {
        return logger;
    }

    public static void setLookandFeel() {
    	if ( lookAndFeelSet )
    	{
    		return;
    	}
        UIDefaults defaults = UIManager.getDefaults();
        Font textFont = defaults.getFont("Label.font");
        if ( textFont == null)
        {
        	textFont = new Font("SansSerif", Font.PLAIN, 12);
        } 
        else 
        {
        	textFont = textFont.deriveFont( Font.PLAIN );
        }
        defaults.put("Label.font", textFont);
        defaults.put("Button.font", textFont);
        defaults.put("Menu.font", textFont);
        defaults.put("MenuItem.font", textFont);
        defaults.put("RadioButton.font", textFont);
        defaults.put("CheckBoxMenuItem.font", textFont);
        defaults.put("CheckBox.font", textFont);
        defaults.put("ComboBox.font", textFont);
        defaults.put("Tree.expandedIcon",RaplaImages.getIcon("/org/rapla/gui/images/eclipse-icons/tree_minus.gif"));
        defaults.put("Tree.collapsedIcon",RaplaImages.getIcon("/org/rapla/gui/images/eclipse-icons/tree_plus.gif"));
        defaults.put("TitledBorder.font", textFont.deriveFont(Font.PLAIN,(float)10.));
        lookAndFeelSet = true;
    }

    protected void initialize() {
        advanceLoading(false);
    	int startupMode = env.getStartupMode();
        final Logger logger = getLogger();
 		if ( startupMode != StartupEnvironment.APPLET && startupMode != StartupEnvironment.WEBSTART)
         {
 			try
 			{
 	        	Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
 	        		public void uncaughtException(Thread t, Throwable e) {
 	        			logger.error("uncaught exception", e);
 	        		}
 	        	});
 			}
 			catch (Throwable ex)
 			{
 				logger.error("Can't set default exception handler-", ex);
 			}
         }
 		
    	setLookandFeel();
    	defaultLanguageChoosen = true;
    	getLogger().info("Starting gui ");
		
        //Add this service to the container

    }

	public ClientFacade getFacade()  {
        return  facade;
    }

    public void start(ConnectInfo connectInfo) throws Exception {
        if (started)
            return;
        try {
        	getLogger().debug("RaplaClient started");
            ClientFacade facade = getFacade();
            facade.addUpdateErrorListener(this);
            StorageOperator operator = facade.getOperator();
            if ( operator instanceof RemoteOperator)
            {
                RemoteConnectionInfo remoteConnection = ((RemoteOperator) operator).getRemoteConnectionInfo();
                remoteConnection.setStatusUpdater( new StatusUpdater()
    	    		{
    	            	private Cursor waitCursor = new Cursor(Cursor.WAIT_CURSOR);
    	            	private Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
    	    			
    	            	public void setStatus(Status status) {
    	    				Cursor cursor =( status == Status.BUSY) ? waitCursor: defaultCursor;
    	    				frameControllerList.setCursor( cursor);
    	            	}
    	    			
    	    		}
    	    		);
            }
            advanceLoading(true);

            logoutAvailable = true;
            if ( connectInfo != null && connectInfo.getUsername() != null)
            {
                if (login( connectInfo))
                {
                    beginRaplaSession();
                    return;
                }
            }
            startLogin();
        } catch (Exception ex) {
            throw ex;
        } finally {
        }
    }

	protected void advanceLoading(boolean finish) {
		try
		{
	    	Class<?> LoadingProgressC= null;
			Object progressBar = null;
	        if ( env.getStartupMode() == StartupEnvironment.CONSOLE)
			{
				LoadingProgressC = getClass().getClassLoader().loadClass("org.rapla.bootstrap.LoadingProgress");
				progressBar = LoadingProgressC.getMethod("inject").invoke(null);
				if ( finish)
				{
					LoadingProgressC.getMethod("close").invoke( progressBar);
				}
				else
				{
					LoadingProgressC.getMethod("advance").invoke( progressBar);
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
    private void beginRaplaSession() throws Exception {
        initLanguage();
        ClientFacade facade = getFacade();

//        StorageOperator operator = facade.getOperator();

        ((FacadeImpl)facade).addDirectModificationListener( new ModificationListener() {
			
			public void dataChanged(ModificationEvent evt) throws RaplaException {
			    calendarModel.get().dataChanged(evt);
			}
        });
//        if ( facade.isClientForServer() )
//        {
//            addContainerProvidedComponent (RaplaClientExtensionPoints.SYSTEM_OPTION_PANEL_EXTENSION , ConnectionOption.class);
//        } 
        
        Preferences systemPreferences = facade.getSystemPreferences();
        //List<PluginDescriptor<ClientServiceContainer>> pluginList = initializePlugins(systemPreferences, ClientServiceContainer.class);
        //addContainerProvidedComponentInstance(ClientServiceContainer.CLIENT_PLUGIN_LIST, pluginList);

        // start client provides
        for ( ClientExtension ext:clientExtensions.get())
        {
            ext.start();
        }

        //FIXME add customizable DateRenderer
        // Add daterender if not provided by the plugins
//        if ( !getContext().has( DateRenderer.class))
//        {
//            addContainerProvidedComponent(DateRenderer.class, RaplaDateRenderer.class);
//        }
        started = true;
        boolean showToolTips = facade.getPreferences(  ).getEntryAsBoolean( RaplaBuilder.SHOW_TOOLTIP_CONFIG_ENTRY, true);
        javax.swing.ToolTipManager.sharedInstance().setEnabled(showToolTips);
        //javax.swing.ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        javax.swing.ToolTipManager.sharedInstance().setInitialDelay( 1000 );
        javax.swing.ToolTipManager.sharedInstance().setDismissDelay( 10000 );
        javax.swing.ToolTipManager.sharedInstance().setReshowDelay( 0 );
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        RaplaFrame mainComponent = raplaFrameProvider.get();
        RaplaGUIComponent.setMainComponent( mainComponent);
        mainComponent.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                try {
                    if ( !isRestartingGUI()) {
                        stop();
                    } else {
                        restartingGUI = false;
                    }
                } catch (Exception ex) {
                    getLogger().error(ex.getMessage(),ex);
                }
            }
    
        });
        MainFrame mainFrame = mainFrameProvider.get();
        fireClientStarted();
        mainFrame.show();
    }

    /*
    protected Set<String> discoverPluginClassnames() throws RaplaException {
        Set<String> pluginNames = super.discoverPluginClassnames();
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        for ( String plugin:pluginNames)
        {
            if ( plugin.toLowerCase().endsWith("serverplugin") || plugin.contains(".server."))
            {
                continue;
            }
            result.add( plugin);
        }
        return pluginNames;
    }
    */
    
    private void initLanguage() throws RaplaException
    {
        ClientFacade facade = getFacade();
        if ( !defaultLanguageChoosen)
        {
            Preferences prefs = facade.edit(facade.getPreferences());
            String currentLanguage = raplaLocale.getLocale().getLanguage();
            prefs.putEntry( RaplaLocale.LANGUAGE_ENTRY, currentLanguage);
            try
            {
                facade.store( prefs);
            }
            catch (Exception e)
            {
                getLogger().error("Can't  store language change", e);
            }
        }
        else
        {
            String language = facade.getPreferences().getEntryAsString( RaplaLocale.LANGUAGE_ENTRY, null);
            if ( language != null)
            {
                DefaultBundleManager localeSelector =  (DefaultBundleManager)bundleManager;
                localeSelector.setLanguage( language );
            }
        }
		AttributeImpl.TRUE_TRANSLATION.setName(i18n.getLang(), i18n.getString("yes"));
        AttributeImpl.FALSE_TRANSLATION.setName(i18n.getLang(), i18n.getString("no"));
    }

    public boolean isRestartingGUI() 
    {
        return restartingGUI;
    }

    public void addRaplaClientListener(RaplaClientListener listener) {
        listenerList.add(listener);
    }

    public void removeRaplaClientListener(RaplaClientListener listener) {
        listenerList.remove(listener);
    }

    public RaplaClientListener[] getRaplaClientListeners() {
        return listenerList.toArray(new RaplaClientListener[]{});
    }

    protected void fireClientClosed(ConnectInfo reconnect) {
        RaplaClientListener[] listeners = getRaplaClientListeners();
        for (int i=0;i<listeners.length;i++)
            listeners[i].clientClosed(reconnect);
    }

    protected void fireClientStarted() {
        RaplaClientListener[] listeners = getRaplaClientListeners();
        for (int i=0;i<listeners.length;i++)
            listeners[i].clientStarted();
    }
    
    protected void fireClientAborted() {
        RaplaClientListener[] listeners = getRaplaClientListeners();
        for (int i=0;i<listeners.length;i++)
            listeners[i].clientAborted();
    }

    public boolean isRunning() {
        return started;
    }

    public void switchTo(User user) throws RaplaException 
    {
        ClientFacade facade = getFacade();
        if ( user == null)
        {
            if ( reconnectInfo == null || reconnectInfo.getConnectAs() == null)
            {
                throw new RaplaException( "Can't switch back because there were no previous logins.");
            }
            final String oldUser = facade.getUser().getUsername();
            String newUser = reconnectInfo.getUsername();
            char[] password = reconnectInfo.getPassword();
            getLogger().info("Login From:" + oldUser  + " To:" + newUser); 
            ConnectInfo reconnectInfo = new ConnectInfo( newUser, password);
            stop( reconnectInfo);
        }
        else
        {
            if  ( reconnectInfo == null)
            {
                throw new RaplaException( "Can't switch to user, because admin login information not provided due missing login.");
                          
            }
            if ( reconnectInfo.getConnectAs() != null)
            {
                throw new RaplaException( "Can't switch to user, because already switched.");
            }
            final String oldUser = reconnectInfo.getUsername();
            final String newUser = user.getUsername();
            getLogger().info("Login From:" + oldUser  + " To:" + newUser); 
            ConnectInfo newInfo = new ConnectInfo( oldUser, reconnectInfo.getPassword(), newUser);
            stop( newInfo);
        }
        // fireUpdateEvent(new ModificationEvent());
    }

    public boolean canSwitchBack() {
        return reconnectInfo != null && reconnectInfo.getConnectAs() != null;
    }
    
    private void stop() {
        stop( null );
    }

    private void stop(ConnectInfo reconnect) {
        if (!started)
            return;

        RaplaGUIComponent.setMainComponent( null );
        try {
            ClientFacade facade = getFacade();
            facade.removeUpdateErrorListener( this);
            if ( facade.isSessionActive())
            {
                facade.logout();
            }
        } catch (RaplaException ex) {
            getLogger().error("Clean logout failed. " + ex.getMessage());
        }
        started = false;
        fireClientClosed(reconnect);
    }

    public void dispose() {
        if (frameControllerList != null)
            frameControllerList.closeAll();
        stop();
        getLogger().debug("RaplaClient disposed");
    }


    private void startLogin()  throws Exception {
    	Command comnmand = new Command()
    	{
			public void execute() throws Exception {
                startLoginInThread();
			}
    	};
		commandScheduler.schedule(comnmand, 0);
    }

    private void startLoginInThread()  {
        final Semaphore loginMutex = new Semaphore(1);
        try {
			final Logger logger = getLogger();
			final LanguageChooser languageChooser = new LanguageChooser(logger,i18n,raplaLocale);
            final DefaultBundleManager localeSelector = (DefaultBundleManager)bundleManager;
            final LoginDialog dlg = LoginDialog.create(env, i18n, localeSelector, logger, raplaLocale, languageChooser.getComponent(), frameControllerList);
            
            Action languageChanged = new AbstractAction()
            {
                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent evt) {
                    try {
                        String lang = languageChooser.getSelectedLanguage();
                        if (lang == null)
                        {
                            defaultLanguageChoosen = true;
                        }
                        else
                        {
                            defaultLanguageChoosen = false;
                            getLogger().debug("Language changing to " + lang);
                            localeSelector.setLanguage(lang);
                            getLogger().info("Language changed " + localeSelector.getLocale().getLanguage() );
                        }
                    } catch (Exception ex) {
                        getLogger().error("Can't change language",ex);
                    }
                }
                
            };
            languageChooser.setChangeAction( languageChanged);
            
            //dlg.setIcon( i18n.getIcon("icon.rapla-small"));
            Action loginAction = new AbstractAction() {
                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent evt) {
                    String username = dlg.getUsername();
                    char[] password = dlg.getPassword();
                    boolean success = false;
                    try {
                        String connectAs = null;
                        reconnectInfo = new ConnectInfo(username, password, connectAs);
                        success = login(reconnectInfo);
                        if ( !success )
                        {
                            dlg.resetPassword();
                            dialogUiFactory.showWarning(i18n.getString("error.login"), new SwingPopupContext(dlg, null));
                        }
                    } 
                    catch (RaplaException ex) 
                    {
                        dlg.resetPassword();
                        dialogUiFactory.showException(ex, new SwingPopupContext(dlg, null));
                    }
                    if ( success) {
                        dlg.close();
                        loginMutex.release();
                        try {
                            beginRaplaSession();
                        } catch (Throwable ex) {
                            dialogUiFactory.showException(ex, null);
                            fireClientAborted();
                        }
                    } // end of else
                }
                
            };
            Action exitAction = new AbstractAction() {
                private static final long serialVersionUID = 1L;
                public void actionPerformed(ActionEvent evt) {
                    dlg.close();
                    loginMutex.release();
                    stop();
                    fireClientAborted();
                }
            };
            loginAction.putValue(Action.NAME,i18n.getString("login"));
            exitAction.putValue(Action.NAME, i18n.getString("exit"));
            dlg.setIconImage(raplaImages.getIconFromKey("icon.rapla_small").getImage());
            dlg.setLoginAction( loginAction);
            dlg.setExitAction( exitAction );
            //dlg.setSize( 480, 270);
            FrameControllerList.centerWindowOnScreen( dlg) ;
            dlg.setVisible( true );

            loginMutex.acquire();
        } catch (Exception ex) {
            getLogger().error("Error during Login ", ex);
            stop();
            fireClientAborted();
        } finally {
            loginMutex.release();
        }
    }

    public void updateError(RaplaException ex) {
        getLogger().error("Error updating data", ex);
    }

    public void disconnected(final String message) {
        if ( started )
        {
        	SwingUtilities.invokeLater( new Runnable() {
				
				public void run() {
					boolean modal = true;
					String title = i18n.getString("restart_client");
					try {
					    Component owner = frameControllerList.getMainWindow();
					    DialogInterface dialog = dialogUiFactory.create(new SwingPopupContext(owner, null), modal, title, message);
						Runnable action = new Runnable() {
							private static final long serialVersionUID = 1L;

							public void run() {
								getLogger().warn("restart");
								restart();
							}
						};
						dialog.setAbortAction(action);
						dialog.getAction(0).setRunnable( action);
						dialog.start(true);
					} catch (Throwable e) {
						getLogger().error(e.getMessage(), e);
					}
			
				}
			});
        }
    }




    public void restart()  
    {
        if ( reconnectInfo != null)
        {
            stop(reconnectInfo);    
        }
    }
    
    public void logout()  
    {
        stop(new ConnectInfo(null, "".toCharArray()));    
    }

    private boolean login(ConnectInfo connect) throws RaplaException 
    {
        UserModule facade = getFacade();
        if (facade.login(connect)) {
            this.reconnectInfo = connect;
            return true;
        } else {
            return false;
        }
    }
    
	public boolean isLogoutAvailable() 
    {
  		return logoutAvailable;
  	}

    private CalendarSelectionModel createCalendarModel() throws RaplaException {
        User user = getFacade().getUser();
        CalendarSelectionModel model = getFacade().newCalendarModel( user);
        model.load( null );
        return model;
    }

}
