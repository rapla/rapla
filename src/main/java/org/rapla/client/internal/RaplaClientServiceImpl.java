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

import org.rapla.ConnectInfo;
import org.rapla.RaplaClient;
import org.rapla.RaplaResources;
import org.rapla.client.ClientService;
import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.client.RaplaClientListener;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.iolayer.WebstartIO;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.*;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.*;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.*;
import org.rapla.gui.images.RaplaImages;
import org.rapla.gui.internal.*;
import org.rapla.gui.internal.common.InternMenus;
import org.rapla.gui.internal.common.RaplaClipboard;
import org.rapla.gui.internal.common.RaplaSwingClipboard;
import org.rapla.gui.internal.edit.EditControllerImpl;
import org.rapla.gui.internal.edit.annotation.*;
import org.rapla.gui.internal.edit.reservation.*;
import org.rapla.gui.internal.view.InfoFactoryImpl;
import org.rapla.gui.internal.view.LicenseInfoUI;
import org.rapla.gui.internal.view.TreeFactoryImpl;
import org.rapla.gui.toolkit.*;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.*;

import javax.inject.Provider;
import javax.swing.*;
import javax.swing.Action;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;

/** Implementation of the ClientService.
*/
public class RaplaClientServiceImpl extends RaplaClient implements ClientServiceContainer,ClientService,UpdateErrorListener
{
    Vector<RaplaClientListener> listenerList = new Vector<RaplaClientListener>();
    RaplaResources i18n;
    boolean started;
    boolean restartingGUI;
    boolean defaultLanguageChoosen;
    FrameControllerList frameControllerList;
    boolean logoutAvailable;
    ConnectInfo reconnectInfo;
	static boolean lookAndFeelSet;
	
	public RaplaClientServiceImpl(StartupEnvironment env) throws Exception {
        super( env);
    }
	
	public RaplaClientServiceImpl(StartupEnvironment env, Provider<RemoteServiceCaller> caller) throws Exception {
        super(  env, caller);
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

    protected Collection<InjectionContext> getSupportedContexts()
    {
        return Arrays.asList(new InjectionContext[]{InjectionContext.client, InjectionContext.swing});
    }

    @Override
    protected void initialize() throws Exception {
        super.initialize();
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
		
    	addContainerProvidedComponentInstance(ClientServiceContainer.class, this);
    	
    	addContainerProvidedComponent(WELCOME_FIELD, LicenseInfoUI.class);
        addContainerProvidedComponent( RaplaImages.class, RaplaImages.class  );
        addContainerProvidedComponent( FrameControllerList.class, FrameControllerList.class  );
        addContainerProvidedComponent( MAIN_COMPONENT, RaplaFrame.class);

        addContainerProvidedComponent( RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION ,UserOption.class);
        addContainerProvidedComponent( RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION , CalendarOption.class);
        addContainerProvidedComponent( RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION , WarningsOption.class);

        addContainerProvidedComponent( RaplaClientExtensionPoints.SYSTEM_OPTION_PANEL_EXTENSION, CalendarOption.class );
        addContainerProvidedComponent( RaplaClientExtensionPoints.SYSTEM_OPTION_PANEL_EXTENSION, RaplaStartOption.class );

        addContainerProvidedComponent( AnnotationEditExtension.ATTRIBUTE_ANNOTATION_EDIT, ColorAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.ATTRIBUTE_ANNOTATION_EDIT, CategorizationAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.ATTRIBUTE_ANNOTATION_EDIT, ExpectedRowsAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.ATTRIBUTE_ANNOTATION_EDIT, ExpectedColumnsAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.ATTRIBUTE_ANNOTATION_EDIT, EmailAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.ATTRIBUTE_ANNOTATION_EDIT, SortingAnnotationEdit.class);
        
        addContainerProvidedComponent( AnnotationEditExtension.DYNAMICTYPE_ANNOTATION_EDIT, LocationAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.DYNAMICTYPE_ANNOTATION_EDIT, ConflictCreationAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.DYNAMICTYPE_ANNOTATION_EDIT, ResourceTreeNameAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.DYNAMICTYPE_ANNOTATION_EDIT, ExportEventNameAnnotationEdit.class);
        addContainerProvidedComponent( AnnotationEditExtension.DYNAMICTYPE_ANNOTATION_EDIT, ExportEventDescriptionAnnotationEdit.class);
        

        RaplaMenubar menuBar = new RaplaMenubar();

        RaplaMenu systemMenu =  new RaplaMenu( InternMenus.FILE_MENU_ROLE.getId() );
        RaplaMenu editMenu = new RaplaMenu( InternMenus.EDIT_MENU_ROLE.getId() );
        editMenu.add( new RaplaSeparator("EDIT_BEGIN"));
        editMenu.add( new RaplaSeparator("EDIT_END"));
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

        boolean webstartEnabled =getContext().lookup(StartupEnvironment.class).getStartupMode() == StartupEnvironment.WEBSTART;

        if (webstartEnabled) {
            addContainerProvidedComponent( IOInterface.class,WebstartIO.class );
        } else {
            addContainerProvidedComponent( IOInterface.class,DefaultIO.class );
        }
        //Add this service to the container
        addContainerProvidedComponentInstance(ClientService.class, this);
        this.i18n = getContext().lookup(RaplaResources.class );
        frameControllerList = inject(FrameControllerList.class);
    }

    class SwingScheduler extends DefaultScheduler
    {
        public SwingScheduler(Logger logger) {
            super(logger, 3);
        }

        @Override
        protected Runnable createTask(final Command command) {
            Runnable timerTask = new Runnable() {
                public void run() {
                    Runnable runnable = SwingScheduler.super.createTask( command);
                    javax.swing.SwingUtilities.invokeLater(runnable);
                }
                public String toString()
                {
                    return command.toString();
                }
            };
            return timerTask;
        }         
    }
    
    @Override
    protected CommandScheduler createCommandQueue() {
        return new SwingScheduler(getLogger());
    }

	public ClientFacade getFacade() throws RaplaContextException {
        return  getContext().lookup( ClientFacade.class);
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
	        if ( getContext().lookup(StartupEnvironment.class).getStartupMode() == StartupEnvironment.CONSOLE)
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
        addContainerProvidedComponentInstance( ClientFacade.class, facade);

        final CalendarSelectionModel model = createCalendarModel();
        addContainerProvidedComponentInstance( CalendarModel.class, model );

        addContainerProvidedComponentInstance( CalendarSelectionModel.class, model );
        StorageOperator operator = facade.getOperator();
		if ( operator instanceof RestartServer)
		{
			addContainerProvidedComponentInstance(RestartServer.class, (RestartServer)operator);
		}
        ((FacadeImpl)facade).addDirectModificationListener( new ModificationListener() {
			
			public void dataChanged(ModificationEvent evt) throws RaplaException {
			    model.dataChanged( evt );
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
        lookupServicesFor(RaplaClientExtensionPoints.CLIENT_EXTENSION);

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
                ServerBundleManager localeSelector =  (ServerBundleManager)getContext().lookup(BundleManager.class);
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
        super.dispose();
        getLogger().debug("RaplaClient disposed");
    }


    private void startLogin()  throws Exception {
    	Command comnmand = new Command()
    	{
			public void execute() throws Exception {
                startLoginInThread();
			}
    	};
		getContext().lookup(CommandScheduler.class).schedule( comnmand, 0);
    }

    private void startLoginInThread()  {
        final Semaphore loginMutex = new Semaphore(1);
        try {
        	final RaplaContext context = getContext();
			final Logger logger = getLogger();
			
            final LanguageChooser languageChooser = inject(LanguageChooser.class);
            
            final LoginDialog dlg = LoginDialog.create(context, languageChooser.getComponent(), frameControllerList);
            
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
                            ServerBundleManager localeSelector = (ServerBundleManager)context.lookup( BundleManager.class );
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
                            RaplaGUIComponent.showWarning(i18n.getString("error.login"), dlg,context,logger);
                        }
                    } 
                    catch (RaplaException ex) 
                    {
                        dlg.resetPassword();
                        RaplaGUIComponent.showException(ex, dlg, context, logger);
                    }
                    if ( success) {
                        dlg.close();
                        loginMutex.release();
                        try {
                            beginRaplaSession();
                        } catch (Throwable ex) {
                        	RaplaGUIComponent.showException(ex, null, context, logger);
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
            RaplaImages inject = getContext().lookup(RaplaImages.class);
            dlg.setIconImage(inject.getIconFromKey("icon.rapla_small").getImage());
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
