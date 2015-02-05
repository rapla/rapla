/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
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
package org.rapla.server.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;

import org.rapla.ConnectInfo;
import org.rapla.RaplaMainContainer;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.internal.UserImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.ComponentInfo;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.internal.RaplaMetaConfigInfo;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.rest.RemoteLogger;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.ResultImpl;
import org.rapla.rest.gwtjsonrpc.common.VoidResult;
import org.rapla.rest.server.RaplaAPIPage;
import org.rapla.rest.server.RaplaAuthRestPage;
import org.rapla.rest.server.RaplaDynamicTypesRestPage;
import org.rapla.rest.server.RaplaEventsRestPage;
import org.rapla.rest.server.RaplaResourcesRestPage;
import org.rapla.rest.server.token.SignedToken;
import org.rapla.rest.server.token.TokenInvalidException;
import org.rapla.rest.server.token.ValidToken;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RaplaServerExtensionPoints;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerService;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.TimeZoneConverter;
import org.rapla.servletpages.DefaultHTMLMenuEntry;
import org.rapla.servletpages.RaplaAppletPageGenerator;
import org.rapla.servletpages.RaplaIndexPageGenerator;
import org.rapla.servletpages.RaplaJNLPPageGenerator;
import org.rapla.servletpages.RaplaPageGenerator;
import org.rapla.servletpages.RaplaStatusPageGenerator;
import org.rapla.servletpages.RaplaStorePage;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteMethodStub;
import org.rapla.storage.dbrm.RemoteServer;
import org.rapla.storage.dbrm.RemoteStorage;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

/** Default implementation of StorageService.
 * <p>Sample configuration 1:
 <pre>
 &lt;storage id="storage" >
 &lt;store>file&lt;/store>
 &lt;/storage>
 </pre>
 * The store value contains the id of a storage-component.
 * Storage-Components are all components that implement the
 * <code>CachableStorageOperator<code> interface.
 * 
 * </p>
 @see ServerService
 */

public class ServerServiceImpl extends ContainerImpl implements StorageUpdateListener, ServerServiceContainer, ServerService, ShutdownService, RemoteMethodFactory<RemoteServer>,RemoteMethodStub
{
    @SuppressWarnings("rawtypes")
    public static Class<RemoteMethodFactory> REMOTE_METHOD_FACTORY = RemoteMethodFactory.class;
    static Class<RaplaPageGenerator> SERVLET_PAGE_EXTENSION = RaplaPageGenerator.class;

    protected CachableStorageOperator operator;
    protected I18nBundle i18n;
    List<PluginDescriptor<ServerServiceContainer>> pluginList;

    ClientFacade facade;
    private AuthenticationStore authenticationStore;
	SignedToken accessTokenSigner;
	SignedToken refreshTokenSigner;
    RemoteSessionImpl standaloneSession;
    ShutdownService shutdownService;
    // 5 Hours until the token expires
    int accessTokenValiditySeconds =  300 * 60;
    public ServerServiceImpl( RaplaContext parentContext, Configuration config, Logger logger) throws RaplaException
    {
        super( parentContext, config, logger );
    	
        addContainerProvidedComponent( TimeZoneConverter.class, TimeZoneConverterImpl.class);
        i18n =  parentContext.lookup( RaplaComponent.RAPLA_RESOURCES );
        Configuration login = config.getChild( "login" );
        String username = login.getChild( "username" ).getValue( null );
        String password = login.getChild( "password" ).getValue( "" );
        RaplaContext context = getContext();

        if ( config.getChildren("facade").length >0 )
        {
        	facade = m_context.lookup(ClientFacade.class);
        }
        else
        {
        	// for old raplaserver.xconf
        	facade = new FacadeImpl( context, config, getLogger().getChildLogger("serverfacade") );
        }
        operator = (CachableStorageOperator) facade.getOperator();
        addContainerProvidedComponentInstance( ServerService.class, this);
        addContainerProvidedComponentInstance( ShutdownService.class, this);
        addContainerProvidedComponentInstance( ServerServiceContainer.class, this);
        addContainerProvidedComponentInstance( CachableStorageOperator.class, operator );
        addContainerProvidedComponentInstance( StorageOperator.class, operator );
        addContainerProvidedComponentInstance( ClientFacade.class, facade );
        addContainerProvidedComponent( SecurityManager.class, SecurityManager.class );
        addRemoteMethodFactory(RemoteStorage.class,RemoteStorageImpl.class, null);
        addRemoteMethodFactory(RemoteLogger.class,RemoteLoggerImpl.class, null);
        addContainerProvidedComponentInstance( REMOTE_METHOD_FACTORY, this, RemoteServer.class.getName() );
        // adds 5 basic pages to the webapplication
        addWebpage( "server",RaplaStatusPageGenerator.class);
        addWebpage( "json",RaplaAPIPage.class);
        addWebpage( "resources",RaplaResourcesRestPage.class);
        addWebpage( "events",RaplaEventsRestPage.class);
        addWebpage( "dynamictypes",RaplaDynamicTypesRestPage.class);
        addWebpage( "auth",RaplaAuthRestPage.class);
        addWebpage( "index",RaplaIndexPageGenerator.class );
        addWebpage( "raplaclient.jnlp",RaplaJNLPPageGenerator.class );
        addWebpage( "raplaclient",RaplaJNLPPageGenerator.class );
        addWebpage( "raplaapplet",RaplaAppletPageGenerator.class );
        addWebpage( "store",RaplaStorePage.class);
        addWebpage( "raplaclient.xconf",RaplaConfPageGenerator.class );
        addWebpage( "raplaclient.xconf",RaplaConfPageGenerator.class);
        
        I18nBundle i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);

        // Index page menu 
        addContainerProvidedComponentInstance( RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT, new DefaultHTMLMenuEntry(context,i18n.getString( "start_rapla_with_webstart" ),"rapla/raplaclient.jnlp") );
        addContainerProvidedComponentInstance( RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT, new DefaultHTMLMenuEntry(context,i18n.getString( "start_rapla_with_applet" ),"rapla?page=raplaapplet") );
        addContainerProvidedComponentInstance( RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT, new DefaultHTMLMenuEntry(context,i18n.getString( "server_status" ),"rapla?page=server") );

        standaloneSession = new RemoteSessionImpl(getContext(), "session");
        
        operator.addStorageUpdateListener( this );
        if ( username != null  )
            operator.connect( new ConnectInfo(username, password.toCharArray()));
        else
            operator.connect();

        Set<String> pluginNames;
        //List<PluginDescriptor<ClientServiceContainer>> pluginList;
        try {
            pluginNames = context.lookup( RaplaMainContainer.PLUGIN_LIST);
        } catch (RaplaContextException ex) {
            throw new RaplaException (ex );
        }
        
        pluginList = new ArrayList<PluginDescriptor<ServerServiceContainer>>( );
        Logger pluginLogger = getLogger().getChildLogger("plugin");
        for ( String plugin:pluginNames)
        {
        	try {
        		boolean found = false;
                try {
                	Class<?> componentClass = ServerServiceImpl.class.getClassLoader().loadClass( plugin );
                    Method[] methods = componentClass.getMethods();
                    for ( Method method:methods)
                    {
                    	if ( method.getName().equals("provideServices"))
                    	{
                    		Class<?> type = method.getParameterTypes()[0];
							if (ServerServiceContainer.class.isAssignableFrom(type))
                    		{
                    			found = true;
                    		}
                    	}
                    }
                } catch (ClassNotFoundException e1) {
                } catch (Exception e1) {
                	getLogger().warn(e1.getMessage());
                	continue;
                }
                if ( found )
                {
                	@SuppressWarnings("unchecked")
					PluginDescriptor<ServerServiceContainer> descriptor = (PluginDescriptor<ServerServiceContainer>) instanciate(plugin, null, logger);
                	pluginList.add(descriptor);
                	pluginLogger.info("Installed plugin "+plugin);
                }
            } catch (RaplaContextException e) {
                if (e.getCause() instanceof ClassNotFoundException) {
                	pluginLogger.error("Could not instanciate plugin "+ plugin, e);
                }
            }
        }
        addContainerProvidedComponent(RaplaKeyStorage.class, RaplaKeyStorageImpl.class);
        
        try {
    		RaplaKeyStorage keyStorage = getContext().lookup( RaplaKeyStorage.class);
    		String secretKey = keyStorage.getRootKeyBase64();
			
			accessTokenSigner = new SignedToken(accessTokenValiditySeconds , secretKey);
			refreshTokenSigner = new SignedToken(-1 , secretKey);
		} catch (Exception e) {
			throw new RaplaException( e.getMessage(), e);
		}
        Preferences preferences = operator.getPreferences( null, true );
        //RaplaConfiguration encryptionConfig = preferences.getEntry(EncryptionService.CONFIG);
        //addRemoteMethodFactory( EncryptionService.class, EncryptionServiceFactory.class);
        String importExportTimeZone = TimeZone.getDefault().getID();
        // get old entries
        RaplaConfiguration entry = preferences.getEntry(RaplaComponent.PLUGIN_CONFIG);
        if ( entry != null)
		{
			Configuration find = entry.find("class", Export2iCalPlugin.PLUGIN_CLASS);
			if  ( find != null)
			{
				String timeZone = find.getChild("TIMEZONE").getValue( null);
				if ( timeZone != null && !timeZone.equals("Etc/UTC"))
				{
					importExportTimeZone = timeZone;
				}
			}
		}
        String timezoneId = preferences.getEntryAsString(RaplaMainContainer.TIMEZONE, importExportTimeZone);
        RaplaLocale raplaLocale = context.lookup(RaplaLocale.class);
        TimeZoneConverter importExportLocale = context.lookup(TimeZoneConverter.class);
        try {
            TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
            TimeZone timeZone = registry.getTimeZone(timezoneId);
            if ( timeZone == null)
            {
                // FIXME create VTimezones for GMT+1-12 and GMT-1-12 
                // if ( timezoneId.startsWith("GMT") )
                String fallback = "Etc/GMT";
                getLogger().error("Timezone " + timezoneId + " not found in ical registry. " + " Using "+ fallback);
                timeZone = registry.getTimeZone(fallback);
                if ( timeZone == null)
                {
                    if (timeZone == null)
                    {
                        throw new RaplaContextException(fallback + " timezone not found in ical registry. ical4j maybe corrupted or not loaded correctyl");
                    }
                }
            }
            ((RaplaLocaleImpl) raplaLocale).setImportExportTimeZone( timeZone);
            ((TimeZoneConverterImpl) importExportLocale).setImportExportTimeZone( timeZone);
            if ( operator instanceof LocalAbstractCachableOperator)
            {
            	((LocalAbstractCachableOperator) operator).setTimeZone( timeZone);
            }
        } catch (Exception rc) {
			getLogger().error("Timezone " + timezoneId + " not found. " + rc.getMessage() + " Using system timezone " + importExportLocale.getImportExportTimeZone());
        }
		initializePlugins( pluginList, preferences );

        if ( context.has( AuthenticationStore.class ) )
        {
            try 
            {
                authenticationStore = context.lookup( AuthenticationStore.class );
                getLogger().info( "Using AuthenticationStore " + authenticationStore.getName() );
            } 
            catch ( RaplaException ex)
            {
                getLogger().error( "Can't initialize configured authentication store. Using default authentication." , ex);
            }
        }
    }

    @Override
    protected Map<String,ComponentInfo> getComponentInfos() {
        return new RaplaMetaConfigInfo();
    }

    public <T> void addRemoteMethodFactory(Class<T> role, Class<? extends RemoteMethodFactory<T>> factory) {
        addRemoteMethodFactory(role, factory, null);
    }

    public <T> void addRemoteMethodFactory(Class<T> role, Class<? extends RemoteMethodFactory<T>> factory, Configuration configuration) {
        addContainerProvidedComponent(REMOTE_METHOD_FACTORY,factory, role.getName(), configuration);
    }
    
    protected RemoteMethodFactory<?> getRemoteMethod(String interfaceName) throws RaplaContextException {
		RemoteMethodFactory<?> factory = lookup( REMOTE_METHOD_FACTORY ,interfaceName);
		return factory;
	}
    
    public <T extends RaplaPageGenerator> void addWebpage(String pagename,
			Class<T> pageClass) {
		addWebpage(pagename, pageClass, null);
	}

	public <T extends RaplaPageGenerator> void addWebpage(String pagename,
			Class<T> pageClass, Configuration configuration) {
		 String lowerCase = pagename.toLowerCase();
		 addContainerProvidedComponent(SERVLET_PAGE_EXTENSION,pageClass, lowerCase, configuration);
	}
	
	public RaplaPageGenerator getWebpage(String page)  {
		try
		{
			String lowerCase = page.toLowerCase();
			RaplaPageGenerator factory = lookup( SERVLET_PAGE_EXTENSION ,lowerCase);
			return factory;
		} catch (RaplaContextException ex)
		{
		    Throwable cause = ex.getCause();
            if ( cause != null)
		    {
		        getLogger().error(cause.getMessage(),cause);
		    }
			return null;
		}
	}

    public void updateError( RaplaException ex )
    {
        if ( getLogger() != null )
            getLogger().error( ex.getMessage(), ex );
        try
        {
            stop();
        }
        catch ( Exception e )
        {
            if ( getLogger() != null )
                getLogger().error( e.getMessage() );
        }
    }
    
    public void objectsUpdated(UpdateResult evt) {
    }


    /**
     * @see org.rapla.server.ServerService#getFacade()
     */
    public ClientFacade getFacade()
    {
        return facade;
    }

    protected void initializePlugins( List<PluginDescriptor<ServerServiceContainer>> pluginList, Preferences preferences ) throws RaplaException
    {
        RaplaConfiguration raplaConfig = preferences.getEntry( RaplaComponent.PLUGIN_CONFIG);
        // Add plugin configs
        for ( Iterator<PluginDescriptor<ServerServiceContainer>> it = pluginList.iterator(); it.hasNext(); )
        {
            PluginDescriptor<ServerServiceContainer> pluginDescriptor = it.next();
            String pluginClassname = pluginDescriptor.getClass().getName();
            Configuration pluginConfig = null;
            if ( raplaConfig != null )
            {
            	// TODO should be replaced with a more descriptive approach instead of looking for the config by guessing from the package name
            	pluginConfig = raplaConfig.find( "class", pluginClassname );
	            // If no plugin config for server is found look for plugin config for client plugin
	            if ( pluginConfig == null )
	            {
            		pluginClassname = pluginClassname.replaceAll("ServerPlugin", "Plugin");
	            	pluginClassname = pluginClassname.replaceAll(".server.", ".client.");
	            	pluginConfig = raplaConfig.find( "class", pluginClassname );
	            	if ( pluginConfig == null)
	            	{
	            		pluginClassname = pluginClassname.replaceAll(".client.", ".");
	            	   	pluginConfig = raplaConfig.find( "class", pluginClassname );
	            	}
	            }
            }
            if ( pluginConfig == null )
            {
                pluginConfig = new DefaultConfiguration( "plugin" );
            }
            pluginDescriptor.provideServices( this, pluginConfig );
        }

        lookupServicesFor(RaplaServerExtensionPoints.SERVER_EXTENSION );
    }

    private void stop() 
    {
    	boolean wasConnected = operator.isConnected();
        operator.removeStorageUpdateListener( this );
        Logger logger = getLogger();
		try
        {
            operator.disconnect();
        } 
        catch (RaplaException e) 
        {
            logger.error( "Could not disconnect operator " , e);
        }
        finally
        {
        }
        if ( wasConnected )
        {
        	logger.info( "Storage service stopped" );
        }
    }

    public void dispose()
    {
        stop();
        super.dispose();
    }

	public StorageOperator getOperator()
    {
        return operator;
    }
    
    public void storageDisconnected(String message)
    {
        try
        {
            stop();
        }
        catch ( Exception e )
        {
            if ( getLogger() != null )
                getLogger().error( e.getMessage() );
        }
    }

    public RemoteServer createService(final RemoteSession session) {
        return new RemoteServer() {
            
            public Logger getLogger()
            {
                if ( session != null)
                {
                    return session.getLogger();
                }
                else
                {
                    return ServerServiceImpl.this.getLogger();
                }
            }
            
            @Override
            public FutureResult<VoidResult> setConnectInfo(RemoteConnectionInfo info) {
                return ResultImpl.VOID;
            }
            
            @Override
            public FutureResult<VoidResult> logout() 
            {
                try
                {
                    if ( session != null)
                    {
                        if ( session.isAuthentified())
                        {
                            User user = session.getUser();
                            if ( user != null)
                            {
                                getLogger().getChildLogger("login").info( "Request Logout " + user.getUsername());
                            }
                            ((RemoteSessionImpl)session).logout();
                        }
                    }
                }
                catch (RaplaException ex)
                {
                    return new ResultImpl<VoidResult>(ex);
                }
                return ResultImpl.VOID;
            }
            
            @Override
            public FutureResult<LoginTokens> login( String username, String password, String connectAs ) 
            {
                LoginCredentials loginCredentials = new LoginCredentials(username,password,connectAs);
                return auth(loginCredentials);
            }
           
            @Override
            public FutureResult<LoginTokens> auth( LoginCredentials credentials ) 
            {
                try
                {
                    User user;
                    String username = credentials.getUsername();
                    String password = credentials.getPassword();
                    String connectAs = credentials.getConnectAs();
                    boolean isStandalone = getContext().has( RemoteMethodStub.class);
                    if ( isStandalone)
                    {
                        String toConnect = connectAs != null && !connectAs.isEmpty() ? connectAs : username;
                        // don't check passwords in standalone version
                        user = operator.getUser( toConnect);
                        if ( user == null)
                        {
                            throw new RaplaSecurityException(i18n.getString("error.login"));
                        }
                        standaloneSession.setUser( user);
                    }
                    else
                    {
                        Logger logger = getLogger().getChildLogger("login");
                        user = authenticate(username, password, connectAs,  logger);
                        ((RemoteSessionImpl)session).setUser( user);
                    }
                    if ( connectAs != null && connectAs.length()> 0)
                    {
                        if (!operator.getUser( username).isAdmin())
                        {
                            throw new SecurityException("Non admin user is requesting change user permission!");
                        }
                    }
                    FutureResult<LoginTokens> generateAccessToken = generateAccessToken(user);
                    return generateAccessToken;
                } catch (RaplaException ex)  {
                    return new ResultImpl<LoginTokens>(ex);
                }
            }

            private FutureResult<LoginTokens> generateAccessToken(User user) throws RaplaException {
                try 
                {
                	String userId = user.getId();
                	Date now = operator.getCurrentTimestamp();
                    Date validUntil = new Date(now.getTime() + 1000 * accessTokenValiditySeconds);
                	String signedToken = accessTokenSigner.newToken( userId,  now);
                    return new ResultImpl<LoginTokens>(new LoginTokens( signedToken, validUntil));
                } catch (Exception e) {
                    throw new RaplaException(e.getMessage());
                }
            }
             
             @Override
             public FutureResult<String> getRefreshToken() 
             {
                 try
                 {
                     User user = getValidUser(session);
                     RaplaKeyStorage keyStore = getContext().lookup(RaplaKeyStorage.class);
                     Collection<String> apiKeys = keyStore.getAPIKeys(user);
                     String refreshToken;
                     if ( apiKeys.size() == 0)
                     {
                         refreshToken = null;
                     }
                     else
                     {
                         refreshToken = apiKeys.iterator().next();
                     }
                     return new ResultImpl<String>(refreshToken);
                 } catch (RaplaException ex)  {
                     return new ResultImpl<String>(ex);
                 }
             }
             
             @Override
             public FutureResult<String> regenerateRefreshToken() 
             {
                 try
                 {
                     User user = getValidUser(session);
                     RaplaKeyStorage keyStore = getContext().lookup(RaplaKeyStorage.class);
                     Date now = operator.getCurrentTimestamp();
                     String generatedAPIKey = refreshTokenSigner.newToken(user.getId(), now);
                     keyStore.storeAPIKey(user, "refreshToken",generatedAPIKey);
                     return new ResultImpl<String>(generatedAPIKey);
                 } catch (Exception ex)  {
                     return new ResultImpl<String>(ex);
                 }
             }

             @Override
             public FutureResult<LoginTokens> refresh(String refreshToken) 
             {
                 try
                 {
                     User user = getUser(refreshToken, refreshTokenSigner);
                     RaplaKeyStorage keyStore = getContext().lookup(RaplaKeyStorage.class);
                     Collection<String> apiKeys = keyStore.getAPIKeys(user);
                     if ( !apiKeys.contains( refreshToken))
                     {
                         throw new RaplaSecurityException("refreshToken not valid");
                     }
                     FutureResult<LoginTokens> generateAccessToken = generateAccessToken(user);
                     return generateAccessToken;
                 } catch (RaplaException ex)  {
                     return new ResultImpl<LoginTokens>(ex);
                 }
             }

            public User getValidUser(final RemoteSession session) throws RaplaContextException, RaplaSecurityException {
                User user = session.getUser();
                 if ( user == null)
                 {
                     throw new RaplaSecurityException(i18n.getString("error.login"));
                 }
                return user;
            }
        };
    }
    
    public User authenticate(String username, String password,String connectAs, Logger logger) throws RaplaException,RaplaSecurityException {
		User user;
		String toConnect = connectAs != null && !connectAs.isEmpty() ? connectAs : username;
		 if ( authenticationStore != null )
		 {
			 logger.info("Checking external authentifiction for user " + username);
			 boolean authenticateExternal;
			 try
			 {
				 authenticateExternal = authenticationStore.authenticate( username, password );
			 }
			 catch (RaplaException ex)
			 {
				 authenticateExternal= false;
				 getLogger().error(ex.getMessage(), ex);
			 }
			 if (authenticateExternal)
			 {
				 //@SuppressWarnings("unchecked")
		         user = operator.getUser( username );
		         if ( user == null )
		         {
		    		 logger.info("Successfull for User " + username +".Creating new Rapla user.");
		             Date now = operator.getCurrentTimestamp();
		             UserImpl newUser = new UserImpl(now,now);
		             newUser.setId( operator.createIdentifier( User.TYPE,1 )[0] );
		             user = newUser;
		         }
		         else
		         {
		            Set<Entity>singleton = Collections.singleton((Entity)user);
					Collection<Entity> editList = operator.editObjects( singleton, null );
					user = (User)editList.iterator().next();
		         }
		         
		         boolean initUser ;
		         try
		         {
		             Category groupCategory = operator.getSuperCategory().getCategory( Permission.GROUP_CATEGORY_KEY );
		    		 logger.debug("Looking for update for rapla user '" + username + "' from external source.");
		             initUser = authenticationStore.initUser( (User) user, username, password, groupCategory );
		         } catch (RaplaSecurityException ex){
		             throw new RaplaSecurityException(i18n.getString("error.login"));
		         }
		         if ( initUser )
		         {
		    		 logger.info("Udating rapla user '" + username + "' from external source.");
		        	 List<Entity>storeList = new ArrayList<Entity>(1);
		             storeList.add( user);
		             List<Entity>removeList = Collections.emptyList();
		             
		             operator.storeAndRemove( storeList, removeList, null );
		         }
		         else
		         {
		    		 logger.debug("User '" + username  + "' already up to date");
		         }
		     }
			 else
			 {
				 logger.info("Now trying to authenticate with local store '" + username + "'");
				 operator.authenticate( username, password );
			 }
			 // do nothing
		 } // if the authenticationStore can't authenticate the user is checked against the local database
		 else
		 {
			 operator.authenticate( username, password );
		 }
		 
		 if ( connectAs != null && connectAs.length() > 0)
		 {
			 logger.info("Successfull login for '" + username  +"' acts as user '" + connectAs + "'");
		 }
		 else
		 {
			 logger.info("Successfull login for '" + username + "'");
		 }
		 user = operator.getUser(toConnect);
		 
		 if ( user == null)
		 {
			 throw new RaplaException("User with username '" + toConnect + "' not found");
		 }
		return user;
	}

  	
  	public void setShutdownService(ShutdownService shutdownService) {
  		this.shutdownService = shutdownService;
  	}
  	
  	public <T> T getWebserviceLocalStub(final Class<T> a) throws RaplaContextException {
  		@SuppressWarnings("unchecked")
		RemoteMethodFactory<T> factory =lookup( REMOTE_METHOD_FACTORY ,a.getName());
  		T service = factory.createService( standaloneSession);
  		return service;
  	}

	public void shutdown(boolean restart) {
		if ( shutdownService != null)
		{
			shutdownService.shutdown(restart);
		}
		else
		{
			getLogger().error("Shutdown service not set");
		}
	}

	public RemoteSession getRemoteSession(HttpServletRequest request) throws RaplaException {
	    User user = getUser(request);
	    RemoteSessionImpl remoteSession = new RemoteSessionImpl(getContext(), user != null ? user.getUsername() : "anonymous");
	    remoteSession.setUser( (User) user);
	   // remoteSession.setAccessToken( token );
	    return remoteSession;
	}

    public User getUser(HttpServletRequest request) throws RaplaException {
        String token = request.getHeader("Authorization");
	    if ( token != null)
	    {
	        String bearerStr = "bearer";
	        int bearer = token.toLowerCase().indexOf(bearerStr);
	        if ( bearer >= 0)
	        {
	            token = token.substring( bearer + bearerStr.length()).trim();
	        }
	    }
	    else
	    {
	        token = request.getParameter("access_token");
	    }
	    User user = null;
	    if ( token == null)
	    {
	        String username = request.getParameter("username");
	        if ( username != null)
	        {
	            user = getUserWithoutPassword(username);
	        }
	    }
	    if ( user == null)
	    {
	        user = getUser( token, accessTokenSigner);
        }
        return user;
    }
	
	@SuppressWarnings("unchecked")
	@Override
    public <T> T createWebservice(Class<T> role, HttpServletRequest request) throws RaplaException
    {
        RemoteMethodFactory<T> remoteMethod = (RemoteMethodFactory<T>) getRemoteMethod( role.getName());
        RemoteSession remoteSession = getRemoteSession(request);
        return  remoteMethod.createService(remoteSession);
    }
	
	@Override
	public boolean hasWebservice(String interfaceName) {
	    try {
            lookup( REMOTE_METHOD_FACTORY ,interfaceName);
        } catch (RaplaContextException e) {
            return false;
        }
	    return true;
	   
	}
	 
	private User getUserWithoutPassword(String username) throws RaplaException
	{
		String connectAs = null;
		User user = authenticate(username, "", connectAs, getLogger());
		return user;
	}
	    
    private User getUser(String tokenString,SignedToken tokenSigner) throws RaplaException
    {
    	if ( tokenString == null)
    	{
    		return null;
    	}
    	 final int s = tokenString.indexOf('$');
    	 if (s <= 0) {
    		 return null;
    	 }

    	final String recvText = tokenString.substring(s + 1);
    	try {
    	    Date now = operator.getCurrentTimestamp();
			ValidToken checkToken = tokenSigner.checkToken(tokenString, recvText, now);
			if ( checkToken == null)
			{
				throw new RaplaSecurityException(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED + " InvalidToken " + tokenString);
			}
		} catch (TokenInvalidException e) {
			throw new RaplaSecurityException(e.getMessage(), e);
		}
    	String userId = recvText;
    	User user =  operator.resolve( userId, User.class);
    	return user;

    }
      

}
