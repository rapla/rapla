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

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.rapla.RaplaResources;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.i18n.server.locales.I18nLocaleLoadUtil;
import org.rapla.components.util.DateTools;
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
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.SimpleProvider;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.internal.RaplaLocaleImpl;
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
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;
import org.rapla.server.servletpages.RaplaAppletPageGenerator;
import org.rapla.server.servletpages.RaplaIndexPageGenerator;
import org.rapla.server.servletpages.RaplaJNLPPageGenerator;
import org.rapla.server.servletpages.RaplaPageGenerator;
import org.rapla.server.servletpages.RaplaStatusPageGenerator;
import org.rapla.server.servletpages.RaplaStorePage;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteServer;
import org.rapla.storage.dbrm.RemoteServiceCaller;
import org.rapla.storage.dbrm.RemoteStorage;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;

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

public class ServerServiceImpl extends ContainerImpl
        implements StorageUpdateListener, ServerServiceContainer, ServerService, ShutdownService, RemoteMethodFactory<RemoteServer>
{

    @SuppressWarnings("rawtypes")
    public static Class<RemoteMethodFactory> REMOTE_METHOD_FACTORY = RemoteMethodFactory.class;
    static Class<RaplaPageGenerator> SERVLET_PAGE_EXTENSION = RaplaPageGenerator.class;

    protected CachableStorageOperator operator;
    protected I18nBundle i18n;
    private final LinkedHashMap<String, Set<String>> countriesForLanguage = new LinkedHashMap<String, Set<String>>();

    private AuthenticationStore authenticationStore;
    SignedToken accessTokenSigner;
    SignedToken refreshTokenSigner;
    ShutdownService shutdownService;

    // 5 Hours until the token expires
    int accessTokenValiditySeconds = 300 * 60;

    private boolean passwordCheckDisabled;

    RemoteSessionImpl adminSession;

    public static class ServerContainerContext
    {
        DataSource dbDatasource;
        String fileDatasource;
        Object mailSession;

        public String getFileDatasource()
        {
            return fileDatasource;
        }

        public Object getMailSession()
        {
            return mailSession;
        }

        public DataSource getDbDatasource()
        {
            return dbDatasource;
        }
    }

    @Inject
    public ServerServiceImpl(Logger logger, ServerContainerContext containerContext, String selectedStorage) throws Exception
    {
        super(logger, new SimpleProvider<RemoteServiceCaller>());
        ((SimpleProvider<RemoteServiceCaller>) remoteServiceCaller).setValue(new RemoteServiceCaller()
        {

            @Override
            public <T> T getRemoteMethod(Class<T> a) throws RaplaContextException
            {
                @SuppressWarnings({ "unchecked", "deprecation" })
                RemoteMethodFactory<T> factory = lookup(REMOTE_METHOD_FACTORY, a.getName());
                T service = factory.createService(adminSession);
                return service;
            }
        });

        adminSession = new RemoteSessionImpl(getLogger().getChildLogger("session"));

        //        URL downloadURL = env.getDownloadURL();
        //        if (downloadURL != null)
        //        {
        //            File file = IOUtil.getFileFrom( downloadURL);
        //            addContainerProvidedComponentInstance( ServerService.CONTEXT_ROOT, file.getPath());
        //        }
        addContainerProvidedComponentInstance(ServerService.TIMESTAMP, new Object()
        {

            public String toString()
            {
                DateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss");
                String formatNow = formatter.format(new Date());
                return formatNow;
            }

        });
        if (containerContext.fileDatasource != null)
        {
            addContainerProvidedComponentInstance(ServerService.ENV_RAPLAFILE, containerContext.fileDatasource);
            addContainerProvidedComponent(FileOperator.class, FileOperator.class);
        }
        if ( selectedStorage == null)
        {
            addContainerProvidedComponentInstance(ServerService.ENV_RAPLAFILE, "data/data.xml");
            addContainerProvidedComponent(FileOperator.class, FileOperator.class);
        }
        if (containerContext.dbDatasource != null)
        {
            addContainerProvidedComponentInstance(DataSource.class, containerContext.dbDatasource);
            addContainerProvidedComponent(DBOperator.class, DBOperator.class);
        }
        if (containerContext.fileDatasource != null && containerContext.dbDatasource != null)
        {
            addContainerProvidedComponent(ImportExportManager.class, ImportExportManagerImpl.class);
        }
        if (containerContext.mailSession != null)
        {
            addContainerProvidedComponentInstance(ServerService.ENV_RAPLAMAIL, containerContext.mailSession);
        }
        initialize();
        addContainerProvidedComponent(TimeZoneConverter.class, TimeZoneConverterImpl.class);
        if (selectedStorage == null || "raplafile".equals( selectedStorage ))
        {
            operator = getContext().lookup(FileOperator.class);
        }
        else if (selectedStorage.equals("rapladb"))
        {
            operator = getContext().lookup(DBOperator.class);
        }
        else
        {
            throw new RaplaException("Unknown datasource " + selectedStorage);
        }
        addContainerProvidedComponentInstance(StorageOperator.class, operator);
        addContainerProvidedComponent(ClientFacade.class, FacadeImpl.class);
        RaplaContext context = getContext();

        addContainerProvidedComponentInstance(ServerService.class, this);
        addContainerProvidedComponentInstance(ServerServiceContainer.class, this);

        addContainerProvidedComponentInstance(ShutdownService.class, this);
        addContainerProvidedComponentInstance(ServerServiceContainer.class, this);
        addContainerProvidedComponentInstance(CachableStorageOperator.class, operator);

        addContainerProvidedComponent(SecurityManager.class, SecurityManager.class);
        addRemoteMethodFactory(RemoteStorage.class, RemoteStorageImpl.class, null);
        addRemoteMethodFactory(RemoteLogger.class, RemoteLoggerImpl.class, null);
        addContainerProvidedComponentInstance(REMOTE_METHOD_FACTORY, this, RemoteServer.class.getName());
        // adds 5 basic pages to the webapplication
        addWebpage("server", RaplaStatusPageGenerator.class);
        addWebpage("json", RaplaAPIPage.class);
        addWebpage("resources", RaplaResourcesRestPage.class);
        addWebpage("events", RaplaEventsRestPage.class);
        addWebpage("dynamictypes", RaplaDynamicTypesRestPage.class);
        addWebpage("auth", RaplaAuthRestPage.class);
        addWebpage("index", RaplaIndexPageGenerator.class);
        addWebpage("raplaclient.jnlp", RaplaJNLPPageGenerator.class);
        addWebpage("raplaclient", RaplaJNLPPageGenerator.class);
        addWebpage("raplaapplet", RaplaAppletPageGenerator.class);
        addWebpage("store", RaplaStorePage.class);

        i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);

        // Index page menu 
        addContainerProvidedComponentInstance(RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT,
                new DefaultHTMLMenuEntry(context, i18n.getString("start_rapla_with_webstart"), "rapla/raplaclient.jnlp"));
        addContainerProvidedComponentInstance(RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT,
                new DefaultHTMLMenuEntry(context, i18n.getString("start_rapla_with_applet"), "rapla?page=raplaapplet"));
        addContainerProvidedComponentInstance(RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT,
                new DefaultHTMLMenuEntry(context, i18n.getString("server_status"), "rapla?page=server"));

        operator.addStorageUpdateListener(this);
        //        if ( username != null  )
        //            operator.connect( new ConnectInfo(username, password.toCharArray()));
        //        else
        operator.connect();

        addContainerProvidedComponent(RaplaKeyStorage.class, RaplaKeyStorageImpl.class);

        try
        {
            RaplaKeyStorage keyStorage = getContext().lookup(RaplaKeyStorage.class);
            String secretKey = keyStorage.getRootKeyBase64();

            accessTokenSigner = new SignedToken(accessTokenValiditySeconds, secretKey);
            refreshTokenSigner = new SignedToken(-1, secretKey);
        }
        catch (Exception e)
        {
            throw new RaplaException(e.getMessage(), e);
        }
        Preferences preferences = operator.getPreferences(null, true);
        //RaplaConfiguration encryptionConfig = preferences.getEntry(EncryptionService.CONFIG);
        //addRemoteMethodFactory( EncryptionService.class, EncryptionServiceFactory.class);
        String importExportTimeZone = TimeZone.getDefault().getID();
        // get old entries
        RaplaConfiguration entry = preferences.getEntry(RaplaComponent.PLUGIN_CONFIG);
        if (entry != null)
        {
            Configuration find = entry.find("class", Export2iCalPlugin.PLUGIN_CLASS);
            if (find != null)
            {
                String timeZone = find.getChild("TIMEZONE").getValue(null);
                if (timeZone != null && !timeZone.equals("Etc/UTC"))
                {
                    importExportTimeZone = timeZone;
                }
            }
        }
        String timezoneId = preferences.getEntryAsString(ContainerImpl.TIMEZONE, importExportTimeZone);
        RaplaLocale raplaLocale = context.lookup(RaplaLocale.class);
        TimeZoneConverter importExportLocale = context.lookup(TimeZoneConverter.class);
        try
        {
            TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
            TimeZone timeZone = registry.getTimeZone(timezoneId);
            if (timeZone == null)
            {
                // FIXME create VTimezones for GMT+1-12 and GMT-1-12 
                // if ( timezoneId.startsWith("GMT") )
                String fallback = "Etc/GMT";
                getLogger().error("Timezone " + timezoneId + " not found in ical registry. " + " Using " + fallback);
                timeZone = registry.getTimeZone(fallback);
                if (timeZone == null)
                {
                    if (timeZone == null)
                    {
                        throw new RaplaContextException(fallback + " timezone not found in ical registry. ical4j maybe corrupted or not loaded correctyl");
                    }
                }
            }
            ((RaplaLocaleImpl) raplaLocale).setImportExportTimeZone(timeZone);
            ((TimeZoneConverterImpl) importExportLocale).setImportExportTimeZone(timeZone);
            if (operator instanceof LocalAbstractCachableOperator)
            {
                ((LocalAbstractCachableOperator) operator).setTimeZone(timeZone);
            }
        }
        catch (Exception rc)
        {
            getLogger().error(
                    "Timezone " + timezoneId + " not found. " + rc.getMessage() + " Using system timezone " + importExportLocale.getImportExportTimeZone());
        }

        User user = getFirstAdmin(operator);
        adminSession.setUser(user);
        initializePlugins(preferences, ServerServiceContainer.class);
        // start server extensions
        lookupServicesFor(RaplaServerExtensionPoints.SERVER_EXTENSION);
        if (context.has(AuthenticationStore.class))
        {
            try
            {
                authenticationStore = context.lookup(AuthenticationStore.class);
                getLogger().info("Using AuthenticationStore " + authenticationStore.getName());
            }
            catch (RaplaException ex)
            {
                getLogger().error("Can't initialize configured authentication store. Using default authentication.", ex);
            }
        }
        {
            final ServerBundleManager bundleManager = (ServerBundleManager) getContext().lookup(BundleManager.class);
            final Set<String> availableLanguages = bundleManager.getAvailableLanguages();
            for (String language : availableLanguages)
            {
                final LinkedHashSet<String> countries = new LinkedHashSet<String>();
                countriesForLanguage.put(language, countries);
                countries.add(language.toUpperCase());
                final String[] isoCountries = Locale.getISOCountries();
                for (String country : isoCountries)
                {
                    final String propertiesFileName = "/org/rapla/components/i18n/server/locales/format_"+language+"_"+country+".properties";
                    final URL resource = RaplaResources.class.getResource(propertiesFileName);
                    if(resource != null)
                    {
                        countries.add(country.toUpperCase());
                    }
                }   
            }
        }
    }

    public void setPasswordCheckDisabled(boolean passwordCheckDisabled)
    {
        this.passwordCheckDisabled = passwordCheckDisabled;
    }

    public <T> T getRemoteMethod(Class<T> a, RemoteSessionImpl standaloneSession) throws RaplaContextException
    {
        @SuppressWarnings({ "unchecked", "deprecation" })
        RemoteMethodFactory<T> factory = lookup(REMOTE_METHOD_FACTORY, a.getName());
        T service = factory.createService(standaloneSession);
        return service;
    }

    public String getFirstAdmin() throws RaplaException
    {
        User user = getFirstAdmin(operator);
        if (user == null)
        {
            return null;
        }
        else
        {
            return user.getUsername();
        }
    }

    public User getFirstAdmin(StorageOperator operator) throws RaplaException
    {
        Set<User> sorted = new TreeSet<User>(User.USER_COMPARATOR);
        sorted.addAll(operator.getUsers());
        for (User u : sorted)
        {
            if (u.isAdmin())
            {
                return u;
            }
        }
        return null;
    }

    public <T> void addRemoteMethodFactory(Class<T> role, Class<? extends RemoteMethodFactory<T>> factory)
    {
        addRemoteMethodFactory(role, factory, null);
    }

    public <T> void addRemoteMethodFactory(Class<T> role, Class<? extends RemoteMethodFactory<T>> factory, Configuration configuration)
    {
        addContainerProvidedComponent(REMOTE_METHOD_FACTORY, factory, role.getName(), configuration);
    }

    @Override
    public <T> T createWebservice(Class<T> role, HttpServletRequest request) throws RaplaException
    {
        String interfaceName = role.getName();
        @SuppressWarnings({ "deprecation", "unchecked" })
        RemoteMethodFactory<T> factory = lookup(REMOTE_METHOD_FACTORY, interfaceName);
        RemoteSession remoteSession = getRemoteSession(request);
        return factory.createService(remoteSession);
    }

    @Override
    public boolean hasWebservice(String interfaceName)
    {
        boolean found = has(REMOTE_METHOD_FACTORY, interfaceName);
        return found;
    }

    public <T extends RaplaPageGenerator> void addWebpage(String pagename, Class<T> pageClass)
    {
        addWebpage(pagename, pageClass, null);
    }

    public <T extends RaplaPageGenerator> void addWebpage(String pagename, Class<T> pageClass, Configuration configuration)
    {
        String lowerCase = pagename.toLowerCase();
        addContainerProvidedComponent(SERVLET_PAGE_EXTENSION, pageClass, lowerCase, configuration);
    }

    public RaplaPageGenerator getWebpage(String page)
    {
        try
        {
            String lowerCase = page.toLowerCase();
            @SuppressWarnings("deprecation")
            RaplaPageGenerator factory = lookup(SERVLET_PAGE_EXTENSION, lowerCase);
            return factory;
        }
        catch (RaplaContextException ex)
        {
            Throwable cause = ex.getCause();
            if (cause != null)
            {
                getLogger().error(cause.getMessage(), cause);
            }
            return null;
        }
    }

    public void updateError(RaplaException ex)
    {
        if (getLogger() != null)
            getLogger().error(ex.getMessage(), ex);
        try
        {
            stop();
        }
        catch (Exception e)
        {
            if (getLogger() != null)
                getLogger().error(e.getMessage());
        }
    }

    public void objectsUpdated(UpdateResult evt)
    {
    }

    private void stop()
    {
        boolean wasConnected = operator.isConnected();
        operator.removeStorageUpdateListener(this);
        Logger logger = getLogger();
        try
        {
            operator.disconnect();
        }
        catch (RaplaException e)
        {
            logger.error("Could not disconnect operator ", e);
        }
        finally
        {
        }
        if (wasConnected)
        {
            logger.info("Storage service stopped");
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
        catch (Exception e)
        {
            if (getLogger() != null)
                getLogger().error(e.getMessage());
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

                    if ( passwordCheckDisabled )
                    {
                        String toConnect = connectAs != null && !connectAs.isEmpty() ? connectAs : username;
                        // don't check passwords in standalone version
                        user = operator.getUser( toConnect);
                        if ( user == null)
                        {
                            throw new RaplaSecurityException(i18n.getString("error.login"));
                        }
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
            
            @Override
            public FutureResult<LocalePackage> locale(String id, String localeString)
            {
                try {
                    if (localeString == null)
                    {
                        final User validUser = getValidUser(session);
                        if (validUser != null)
                        {
                            final Preferences preferences = operator.getPreferences(validUser, true);
                            final String entry = preferences.getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, null);
                            if (entry != null)
                            {
                                localeString = new Locale(entry).toString();
                            }
                        }
                        if (localeString == null)
                        {
                            localeString = raplaLocale.getLocale().toString();
                        }
                    }
                    Locale locale = DateTools.getLocale(localeString);
                    final I18nLocaleFormats formats = I18nLocaleLoadUtil.read(locale);
                    Map<String, Map<String, String>> bundles = new LinkedHashMap<String, Map<String, String>>();
                    for (Class<AbstractBundle> clazz : ServerServiceImpl.this.getResourceBundles()) {
                        AbstractBundle i18n = getContext().lookup(clazz);
                        String packageId = i18n.getPackageId();
                        final Collection<String> keys = i18n.getKeys();
                        final LinkedHashMap<String, String> raplaResourceIdMap = new LinkedHashMap<String, String>();
                        bundles.put(packageId, raplaResourceIdMap);
                        for (String key : keys) {
                            raplaResourceIdMap.put(key, i18n.getString(key, locale));
                        }
                    }
                    final LocalePackage localePackage = new LocalePackage(formats, bundles, ServerBundleManager.loadAvailableLanguages());
                    return new ResultImpl<LocalePackage>(localePackage);
                }
                catch (Exception e1)
                {
                    getLogger().error("No locales found", e1);
                    return new ResultImpl<LocalePackage>(e1);
                }
            }
            @Override
            public FutureResult<Map<String, Set<String>>> countries(Set<String> languages)
            {
                final LinkedHashMap<String, Set<String>> result = new LinkedHashMap<String, Set<String>>();
                if(languages != null)
                {
                    for (String language : languages)
                    {
                        final Set<String> countries = countriesForLanguage.get(language);
                        if(countries != null)
                        {
                            result.put(language, countries);
                        }
                    }
                }
                return new ResultImpl<Map<String,Set<String>>>(result);
            }
        };
    }

    public User authenticate(String username, String password, String connectAs, Logger logger) throws RaplaException, RaplaSecurityException
    {
        User user;
        String toConnect = connectAs != null && !connectAs.isEmpty() ? connectAs : username;
        logger.info("User '" + username + "' is requesting login.");
        if (authenticationStore != null)
        {
            logger.info("Checking external authentifiction for user " + username);
            boolean authenticateExternal;
            try
            {
                authenticateExternal = authenticationStore.authenticate(username, password);
            }
            catch (RaplaException ex)
            {
                authenticateExternal = false;
                getLogger().error(ex.getMessage(), ex);
            }
            if (authenticateExternal)
            {
                //@SuppressWarnings("unchecked")
                user = operator.getUser(username);
                if (user == null)
                {
                    logger.info("Successfull for User " + username + ".Creating new Rapla user.");
                    Date now = operator.getCurrentTimestamp();
                    UserImpl newUser = new UserImpl(now, now);
                    newUser.setId(operator.createIdentifier(User.TYPE, 1)[0]);
                    user = newUser;
                }
                else
                {
                    Set<Entity> singleton = Collections.singleton((Entity) user);
                    Collection<Entity> editList = operator.editObjects(singleton, null);
                    user = (User) editList.iterator().next();
                }

                boolean initUser;
                try
                {
                    Category groupCategory = operator.getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
                    logger.debug("Looking for update for rapla user '" + username + "' from external source.");
                    initUser = authenticationStore.initUser((User) user, username, password, groupCategory);
                }
                catch (RaplaSecurityException ex)
                {
                    throw new RaplaSecurityException(i18n.getString("error.login"));
                }
                if (initUser)
                {
                    logger.info("Udating rapla user '" + username + "' from external source.");
                    List<Entity> storeList = new ArrayList<Entity>(1);
                    storeList.add(user);
                    List<Entity> removeList = Collections.emptyList();

                    operator.storeAndRemove(storeList, removeList, null);
                }
                else
                {
                    logger.info("User '" + username + "' already up to date");
                }
            }
            else
            {
                logger.info("Now trying to authenticate with local store '" + username + "'");
                operator.authenticate(username, password);
            }
            // do nothing
        } // if the authenticationStore can't authenticate the user is checked against the local database
        else
        {
            logger.info("Check password for " + username);
            operator.authenticate(username, password);
        }

        if (connectAs != null && connectAs.length() > 0)
        {
            logger.info("Successfull login for '" + username + "' acts as user '" + connectAs + "'");
        }
        else
        {
            logger.info("Successfull login for '" + username + "'");
        }
        user = operator.getUser(toConnect);

        if (user == null)
        {
            throw new RaplaException("User with username '" + toConnect + "' not found");
        }
        return user;
    }

    public void setShutdownService(ShutdownService shutdownService)
    {
        this.shutdownService = shutdownService;
    }

    public void shutdown(boolean restart)
    {
        if (shutdownService != null)
        {
            shutdownService.shutdown(restart);
        }
        else
        {
            getLogger().error("Shutdown service not set");
        }
    }

    public RemoteSession getRemoteSession(HttpServletRequest request) throws RaplaException
    {
        User user = getUser(request);
        RemoteSessionImpl remoteSession = new RemoteSessionImpl(getLogger().getChildLogger(user != null ? user.getUsername() : "anonymous"));
        remoteSession.setUser((User) user);
        // remoteSession.setAccessToken( token );
        return remoteSession;
    }

    public User getUser(HttpServletRequest request) throws RaplaException
    {
        String token = request.getHeader("Authorization");
        if (token != null)
        {
            String bearerStr = "bearer";
            int bearer = token.toLowerCase().indexOf(bearerStr);
            if (bearer >= 0)
            {
                token = token.substring(bearer + bearerStr.length()).trim();
            }
        }
        else
        {
            token = request.getParameter("access_token");
        }
        User user = null;
        if (token == null)
        {
            String username = request.getParameter("username");
            if (username != null)
            {
                user = getUserWithoutPassword(username);
            }
        }
        if (user == null)
        {
            user = getUser(token, accessTokenSigner);
        }
        return user;
    }

    private User getUserWithoutPassword(String username) throws RaplaException
    {
        String connectAs = null;
        User user = authenticate(username, "", connectAs, getLogger());
        return user;
    }

    private User getUser(String tokenString, SignedToken tokenSigner) throws RaplaException
    {
        if (tokenString == null)
        {
            return null;
        }
        final int s = tokenString.indexOf('$');
        if (s <= 0)
        {
            return null;
        }

        final String recvText = tokenString.substring(s + 1);
        try
        {
            Date now = operator.getCurrentTimestamp();
            ValidToken checkToken = tokenSigner.checkToken(tokenString, recvText, now);
            if (checkToken == null)
            {
                throw new RaplaSecurityException(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED + " InvalidToken " + tokenString);
            }
        }
        catch (TokenInvalidException e)
        {
            throw new RaplaSecurityException(e.getMessage(), e);
        }
        String userId = recvText;
        User user = operator.resolve(userId, User.class);
        return user;

    }

}
