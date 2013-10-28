/*--------------------------------------------------------------------------*
main.raplaContainer.dispose();
             | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

import org.rapla.ConnectInfo;
import org.rapla.RaplaMainContainer;
import org.rapla.client.ClientService;
import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.client.RaplaClientListener;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.iolayer.WebstartIO;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.Mutex;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.UpdateErrorListener;
import org.rapla.facade.UserModule;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.ComponentInfo;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaMetaConfigInfo;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.EditController;
import org.rapla.gui.InfoFactory;
import org.rapla.gui.MenuFactory;
import org.rapla.gui.ReservationController;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.images.Images;
import org.rapla.gui.internal.CalendarOption;
import org.rapla.gui.internal.ConnectionOption;
import org.rapla.gui.internal.MainFrame;
import org.rapla.gui.internal.MenuFactoryImpl;
import org.rapla.gui.internal.RaplaDateRenderer;
import org.rapla.gui.internal.RaplaStartOption;
import org.rapla.gui.internal.UserOption;
import org.rapla.gui.internal.common.InternMenus;
import org.rapla.gui.internal.common.RaplaClipboard;
import org.rapla.gui.internal.edit.EditControllerImpl;
import org.rapla.gui.internal.edit.reservation.ConflictReservationCheck;
import org.rapla.gui.internal.edit.reservation.DefaultReservationCheck;
import org.rapla.gui.internal.edit.reservation.ReservationControllerImpl;
import org.rapla.gui.internal.view.InfoFactoryImpl;
import org.rapla.gui.internal.view.LicenseInfoUI;
import org.rapla.gui.internal.view.TreeFactoryImpl;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.ErrorDialog;
import org.rapla.gui.toolkit.FrameControllerList;
import org.rapla.gui.toolkit.RaplaFrame;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaMenubar;
import org.rapla.gui.toolkit.RaplaSeparator;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteServiceCaller;
import org.rapla.storage.dbrm.RestartServer;
import org.rapla.storage.dbrm.StatusUpdater;

/** Implementation of the ClientService.
*/
public class RaplaClientServiceImpl extends ContainerImpl implements ClientServiceContainer,ClientService,UpdateErrorListener
{
    Vector<RaplaClientListener> listenerList = new Vector<RaplaClientListener>();
    I18nBundle i18n;
    boolean started;
    boolean restartingGUI;
    String facadeName;
    boolean defaultLanguageChoosen;
    FrameControllerList frameControllerList;
    Configuration config;
    boolean logoutAvailable;
    ConnectInfo reconnectInfo;
	
    static boolean lookAndFeelSet;
	private CommandScheduler commandQueue;
	

	public RaplaClientServiceImpl(RaplaContext parentContext,Configuration config,Logger logger) throws RaplaException {
        super(parentContext,config, logger);
        this.config = config;
    }

    @Override
    protected Map<String,ComponentInfo> getComponentInfos() {
        return new RaplaMetaConfigInfo();
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
        defaults.put("Tree.expandedIcon",Images.getIcon("/org/rapla/gui/images/eclipse-icons/tree_minus.gif"));
        defaults.put("Tree.collapsedIcon",Images.getIcon("/org/rapla/gui/images/eclipse-icons/tree_plus.gif"));
        defaults.put("TitledBorder.font", textFont.deriveFont(Font.PLAIN,(float)10.));
        
        lookAndFeelSet = true;
    }
    
    protected void init() throws RaplaException {
    	advanceLoading(false);
    	StartupEnvironment env = getContext().lookup(StartupEnvironment.class);
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
		
        super.init( );

        facadeName = m_config.getChild("facade").getValue("*");

        addContainerProvidedComponent( WELCOME_FIELD, LicenseInfoUI.class  );
        addContainerProvidedComponent( MAIN_COMPONENT, RaplaFrame.class);
        
        // overwrite commandqueue because we need to synchronize with swing
        commandQueue = createCommandQueue();
		addContainerProvidedComponentInstance( CommandScheduler.class, commandQueue);

            
        addContainerProvidedComponent( RaplaClipboard.class, RaplaClipboard.class );
        addContainerProvidedComponent( TreeFactory.class, TreeFactoryImpl.class );
        addContainerProvidedComponent( MenuFactory.class, MenuFactoryImpl.class );
        addContainerProvidedComponent( InfoFactory.class, InfoFactoryImpl.class );
        addContainerProvidedComponent( EditController.class, EditControllerImpl.class );
        addContainerProvidedComponent( ReservationController.class, ReservationControllerImpl.class );
        addContainerProvidedComponent( RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION ,UserOption.class);
        addContainerProvidedComponent( RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION , CalendarOption.class);
        addContainerProvidedComponent( RaplaClientExtensionPoints.SYSTEM_OPTION_PANEL_EXTENSION, CalendarOption.class );
        addContainerProvidedComponent( RaplaClientExtensionPoints.SYSTEM_OPTION_PANEL_EXTENSION, RaplaStartOption.class );
        
        frameControllerList = new FrameControllerList(getLogger().getChildLogger("framelist"));
        addContainerProvidedComponentInstance(FrameControllerList.class,frameControllerList);

        RaplaMenubar menuBar = new RaplaMenubar();

        RaplaMenu systemMenu =  new RaplaMenu( InternMenus.FILE_MENU_ROLE.getId() );
        RaplaMenu editMenu = new RaplaMenu( InternMenus.EDIT_MENU_ROLE.getId() );
        RaplaMenu viewMenu = new RaplaMenu( InternMenus.VIEW_MENU_ROLE.getId() );
        RaplaMenu helpMenu = new RaplaMenu( InternMenus.EXTRA_MENU_ROLE.getId() );

        RaplaMenu newMenu = new RaplaMenu( InternMenus.NEW_MENU_ROLE.getId() );
        RaplaMenu settingsMenu = new RaplaMenu( InternMenus.CALENDAR_SETTINGS.getId());
        RaplaMenu adminMenu = new RaplaMenu( InternMenus.ADMIN_MENU_ROLE.getId() );
        RaplaMenu importMenu = new RaplaMenu( InternMenus.IMPORT_MENU_ROLE.getId());
        RaplaMenu exportMenu = new RaplaMenu( InternMenus.EXPORT_MENU_ROLE.getId());
        
        menuBar.add( systemMenu );
        menuBar.add( editMenu );
        menuBar.add( viewMenu );
        menuBar.add( helpMenu );
        
        addContainerProvidedComponentInstance( SESSION_MAP, new HashMap<Object,Object>());

        addContainerProvidedComponentInstance( InternMenus.MENU_BAR,  menuBar);
        addContainerProvidedComponentInstance( InternMenus.FILE_MENU_ROLE, systemMenu );
        addContainerProvidedComponentInstance( InternMenus.EDIT_MENU_ROLE,  editMenu);
        addContainerProvidedComponentInstance( InternMenus.VIEW_MENU_ROLE,  viewMenu);
        addContainerProvidedComponentInstance( InternMenus.ADMIN_MENU_ROLE,  adminMenu);
        addContainerProvidedComponentInstance( InternMenus.IMPORT_MENU_ROLE, importMenu );
        addContainerProvidedComponentInstance( InternMenus.EXPORT_MENU_ROLE, exportMenu );
        addContainerProvidedComponentInstance( InternMenus.NEW_MENU_ROLE, newMenu );
        addContainerProvidedComponentInstance( InternMenus.CALENDAR_SETTINGS, settingsMenu );
        addContainerProvidedComponentInstance( InternMenus.EXTRA_MENU_ROLE, helpMenu );

        editMenu.add( new RaplaSeparator("EDIT_BEGIN"));
        editMenu.add( new RaplaSeparator("EDIT_END"));
        
        addContainerProvidedComponent(RaplaClientExtensionPoints.RESERVATION_SAVE_CHECK, DefaultReservationCheck.class);
        addContainerProvidedComponent(RaplaClientExtensionPoints.RESERVATION_SAVE_CHECK, ConflictReservationCheck.class);
        
        boolean webstartEnabled =getContext().lookup(StartupEnvironment.class).getStartupMode() == StartupEnvironment.WEBSTART;

        if (webstartEnabled) {
            addContainerProvidedComponent( IOInterface.class,WebstartIO.class );
        } else {
            addContainerProvidedComponent( IOInterface.class,DefaultIO.class );
        }
        //Add this service to the container
        addContainerProvidedComponentInstance(ClientService.class, this);
    }
    
	protected Runnable createTask(final Command command) {
		Runnable timerTask = new Runnable() {
			public void run() {
				Runnable runnable = RaplaClientServiceImpl.super.createTask( command);
				javax.swing.SwingUtilities.invokeLater(runnable);
			}
		};
		return timerTask;
	}

    public ClientFacade getFacade() throws RaplaContextException {
        return  lookup(ClientFacade.class, facadeName);
    }

    public void start(ConnectInfo connectInfo) throws Exception {
        if (started)
            return;
        try {
        	getLogger().debug("RaplaClient started");
            i18n = getContext().lookup(RaplaComponent.RAPLA_RESOURCES );
            ClientFacade facade = getFacade();
            
            facade.addUpdateErrorListener(this);
            StorageOperator operator = facade.getOperator();
            if ( operator instanceof RemoteServiceCaller)
            {
	            ((RemoteServiceCaller)operator).setStatusUpdater( new StatusUpdater()
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

            ConnectInfo configuredConnectInfo = facade.getConfiguredConnectInfo();
            logoutAvailable = configuredConnectInfo == null;
            if ( connectInfo == null)
            {
                connectInfo= configuredConnectInfo;
            }

            
            if ( connectInfo != null)
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
	        if ( getContext().lookup(StartupEnvironment.class).getStartupMode() == StartupEnvironment.CONSOLE)
			{
				LoadingProgressC = getClass().getClassLoader().loadClass("org.rapla.bootstrap.LoadingProgress");
				progressBar = LoadingProgressC.getMethod("getInstance").invoke(null);
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
    private void beginRaplaSession() throws RaplaException {
        initLanguage();
         
        final CalendarSelectionModel model = createCalendarModel();
        addContainerProvidedComponentInstance( CalendarModel.class, model );
        addContainerProvidedComponentInstance( CalendarSelectionModel.class, model );
        ClientFacade facade = getFacade();
		addContainerProvidedComponentInstance( ClientFacade.class, facade);
        StorageOperator operator = facade.getOperator();
		if ( operator instanceof RestartServer)
		{
			addContainerProvidedComponentInstance(RestartServer.class, (RestartServer)operator);
		}
		if ( operator instanceof RemoteServiceCaller)
		{
			addContainerProvidedComponentInstance(RemoteServiceCaller.class, (RemoteServiceCaller)operator);
		}
        ((FacadeImpl)facade).addDirectModificationListener( new ModificationListener() {
			
			public boolean isInvokedOnAWTEventQueue() {
				return false;
			}

			public void dataChanged(ModificationEvent evt) throws RaplaException {
			    model.dataChanged( evt );
			}
        });
        if ( facade.isClientForServer() )
        {
            addContainerProvidedComponent (RaplaClientExtensionPoints.SYSTEM_OPTION_PANEL_EXTENSION , ConnectionOption.class);
        } 
        
        Set<String> pluginNames;
        //List<PluginDescriptor<ClientServiceContainer>> pluginList;
        try {
            pluginNames = getContext().lookup( RaplaMainContainer.PLUGIN_LIST);
        } catch (RaplaContextException ex) {
            throw new RaplaException (ex );
        }
        
        List<PluginDescriptor<ClientServiceContainer>> pluginList = new ArrayList<PluginDescriptor<ClientServiceContainer>>( );
        Logger logger = getLogger().getChildLogger("plugin");
        for ( String plugin:pluginNames)
        {
        	try {
        	    boolean found = false;
        	    try {
        	    	if ( plugin.toLowerCase().endsWith("serverplugin") || plugin.contains(".server."))
        	    	{
        	    		continue;
        	    	}
        	    	Class<?> componentClass = RaplaClientServiceImpl.class.getClassLoader().loadClass( plugin );
                    Method[] methods = componentClass.getMethods();
                    for ( Method method:methods)
                    {
                    	if ( method.getName().equals("provideServices"))
                    	{
                    		Class<?> type = method.getParameterTypes()[0];
							if (ClientServiceContainer.class.isAssignableFrom(type))
                    		{
                    			found = true;
                    		}
                    	}
                    }
                } catch (ClassNotFoundException ex) {
                	continue;
                } catch (NoClassDefFoundError ex) {
                	getLogger().error("Error loading plugin " + plugin + " " +ex.getMessage());
                	continue;
                } catch (Exception e1) {
                	getLogger().error("Error loading plugin " + plugin + " " +e1.getMessage());
                	continue;
                }
                if ( found )
                {
                	@SuppressWarnings("unchecked")
					PluginDescriptor<ClientServiceContainer> descriptor = (PluginDescriptor<ClientServiceContainer>) instanciate(plugin, null, logger);
                	pluginList.add(descriptor);
                	logger.info("Installed plugin "+plugin);
                }
            } catch (RaplaContextException e) {
                if (e.getCause() instanceof ClassNotFoundException) {
                    logger.error("Could not instanciate plugin "+ plugin, e);
                }
            }
        }
        addContainerProvidedComponentInstance(ClientServiceContainer.CLIENT_PLUGIN_LIST, pluginList);
        initializePlugins( pluginList, facade.getPreferences( null) );
        
        // Add daterender if not provided by the plugins
        if ( !getContext().has( DateRenderer.class))
        {
            addContainerProvidedComponent( DateRenderer.class, RaplaDateRenderer.class );
        }
        started = true;
        User user = model.getUser();
        boolean showToolTips = facade.getPreferences( user ).getEntryAsBoolean( RaplaBuilder.SHOW_TOOLTIP_CONFIG_ENTRY, true);
        javax.swing.ToolTipManager.sharedInstance().setEnabled(showToolTips);
        //javax.swing.ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        javax.swing.ToolTipManager.sharedInstance().setInitialDelay( 1000 );
        javax.swing.ToolTipManager.sharedInstance().setDismissDelay( 10000 );
        javax.swing.ToolTipManager.sharedInstance().setReshowDelay( 0 );
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        RaplaFrame mainComponent = getContext().lookup( MAIN_COMPONENT );
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
        MainFrame mainFrame = new MainFrame( getContext());
        fireClientStarted();
        mainFrame.show();
    }

    private void initLanguage() throws RaplaException, RaplaContextException
    {
        ClientFacade facade = getFacade();
        if ( !defaultLanguageChoosen)
        {
            Preferences prefs = facade.edit(facade.getPreferences());
            RaplaLocale raplaLocale =  getContext().lookup(RaplaLocale.class );
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
                LocaleSelector localeSelector =  getContext().lookup(LocaleSelector.class);
                localeSelector.setLanguage( language );
            }
        }
    }

    protected void initializePlugins(List<PluginDescriptor<ClientServiceContainer>> pluginList, Preferences preferences) throws RaplaException {
        RaplaConfiguration raplaConfig =preferences.getEntry(RaplaComponent.PLUGIN_CONFIG);
        // Add plugin configs
        for ( Iterator<PluginDescriptor<ClientServiceContainer>> it = pluginList.iterator(); it.hasNext();  ) {
            PluginDescriptor<ClientServiceContainer> pluginDescriptor = it.next();
            String pluginClassname = pluginDescriptor.getClass().getName();
            Configuration pluginConfig = null;
            if ( raplaConfig != null) {
                pluginConfig = raplaConfig.find("class", pluginClassname);
            }
            if ( pluginConfig == null) {
                pluginConfig = new DefaultConfiguration("plugin");
            }
            pluginDescriptor.provideServices( this, pluginConfig );
        }

        //Collection<?> clientPlugins = getAllServicesForThisContainer(RaplaExtensionPoints.CLIENT_EXTENSION);
        // start plugins
        getAllServicesForThisContainer(RaplaClientExtensionPoints.CLIENT_EXTENSION);
        //        for (Iterator<?> it = clientPlugins.iterator();it.hasNext();) {
//            String hint = (String) it.next();
//            try {
//                getContext().lookup( RaplaExtensionPoints.CLIENT_EXTENSION , hint);
//                getLogger().info( "Initialize " + hint );
//            } catch (RaplaContextException ex ) {
//                getLogger().error( "Can't initialize " + hint, ex );
//            }
//        }
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

        try {
            ClientFacade facade = getFacade();
            facade.removeUpdateErrorListener( this);
            if ( facade.isSessionActive())
            {
                facade.logout();
            }
        } catch (RaplaException ex) {
            getLogger().error("Clean logout failed",ex);
        }
        started = false;
        fireClientClosed(reconnect);
    }

    public void dispose() {
        if (frameControllerList != null)
            frameControllerList.closeAll();
        stop();
        if ( commandQueue != null)
        {
        	((DefaultScheduler)commandQueue).cancel();
        }
        super.dispose();
        getLogger().debug("RaplaClient disposed");
    }


    private void startLogin()  throws Exception {
    	Command object = new Command()
    	{
			public void execute() throws Exception {
                startLoginInThread();
			}
    	};
		commandQueue.schedule( object, 0);
    }

    private void startLoginInThread()  {
        final Semaphore loginMutex = new Semaphore(1);
        try {
            final LanguageChooser languageChooser = new LanguageChooser( getLogger(), getContext());
            
            final LoginDialog dlg = LoginDialog.create(getContext(), languageChooser.getComponent());
            
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
                            getLogger().debug("Language changing to " + lang );
                            LocaleSelector localeSelector = getContext().lookup( LocaleSelector.class );
                            localeSelector.setLanguage(lang);
                            getLogger().info("Language changed " + localeSelector.getLanguage() );
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
                            showWarning(i18n.getString("error.login"), dlg);
                        }
                    } 
                    catch (RaplaException ex) 
                    {
                        dlg.resetPassword();
                        showException(ex,dlg);
                    }
                    if ( success) {
                        dlg.close();
                        loginMutex.release();
                        try {
                            beginRaplaSession();
                        } catch (Throwable ex) {
                            showException(ex, null);
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
            exitAction.putValue(Action.NAME,i18n.getString("exit"));
            dlg.setIconImage(i18n.getIcon("icon.rapla_small").getImage());
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

    /**
     * @see org.rapla.facade.UpdateErrorListener#disconnected()
     */
    public void disconnected(final String message) {
        if ( started )
        {
        	SwingUtilities.invokeLater( new Runnable() {
				
				public void run() {
					boolean modal = true;
					String title = i18n.getString("restart_client");
					try {
					    Component owner = frameControllerList.getMainWindow();
						DialogUI dialog = DialogUI.create(getContext(), owner, modal, title, message);
						Action action = new AbstractAction() {
							private static final long serialVersionUID = 1L;

							public void actionPerformed(ActionEvent e) {
								getLogger().warn("restart");
								restart();
							}
						};
						dialog.setAbortAction(action);
						dialog.getButton(0).setAction( action);
						dialog.start();
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

    public void showException(Throwable ex,Component component) 
    {
        try {
            ErrorDialog dialog = new ErrorDialog(getContext());
            dialog.showExceptionDialog(ex,component);
        } catch (RaplaException ex2) {
            getLogger().error(ex2.getMessage(),ex2);
        }
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

    public void showWarning(String warning,Component owner) 
    {
        try {
            ErrorDialog dialog = new ErrorDialog(getContext());
            dialog.showWarningDialog(warning,owner);
        } catch (RaplaException ex2) {
            getLogger().error(ex2.getMessage(),ex2);
        }
    }


    private CalendarSelectionModel createCalendarModel() throws RaplaException {
        User user = getFacade().getUser();
        CalendarSelectionModel model = getFacade().newCalendarModel( user);
        model.load( null );
        return model;
    }

}
