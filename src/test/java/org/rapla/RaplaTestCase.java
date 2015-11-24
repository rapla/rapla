/*--------------------------------------------------------------------------*
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
package org.rapla;

import junit.framework.TestCase;
import org.eclipse.jetty.server.Server;
import org.junit.Before;
import org.rapla.client.internal.RaplaClientServiceImpl;
import org.rapla.client.swing.toolkit.ErrorDialog;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.domain.permission.DefaultPermissionControllerSupport;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.ConsoleLogger;
import org.rapla.framework.logger.Logger;
import org.rapla.jsonrpc.client.EntryPointFactory;
import org.rapla.jsonrpc.client.swing.BasicRaplaHTTPConnector;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.dagger.DaggerServerCreator;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbrm.MyCustomConnector;
import org.rapla.storage.dbrm.RemoteAuthentificationService;
import org.rapla.storage.dbrm.RemoteAuthentificationService_JavaJsonProxy;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteOperator;
import org.rapla.storage.dbrm.RemoteStorage;
import org.rapla.storage.dbrm.RemoteStorage_JavaJsonProxy;
import org.xml.sax.InputSource;

import javax.inject.Provider;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class RaplaTestCase extends TestCase
{
    protected RaplaClientServiceImpl raplaContainer;
    Logger logger = new ConsoleLogger(ConsoleLogger.LEVEL_WARN).getChildLogger("test");

    public static String TEST_SRC_FOLDER_NAME = "src/test/resources";
    public static String TEST_FOLDER_NAME = "target/test";
    protected RaplaStartupEnvironment env = new RaplaStartupEnvironment();

    int port = 8052;

    public static ServerServiceContainer createServer( Logger logger, String xmlFile) throws Exception
    {
        ServerContainerContext containerContext = new ServerContainerContext();
        containerContext.setFileDatasource(getTestDataFile(xmlFile));
        return createServer(logger, containerContext);
    }

    public static ServerServiceContainer createServer(Logger logger, ServerContainerContext containerContext) throws Exception
    {
        final ServerServiceContainer serverServiceContainer = DaggerServerCreator.create(logger, containerContext);
        initFileHack();
        return serverServiceContainer;
    }

    private static void initFileHack()
    {
        FileOperator.setFileIO(new FileOperator.DefaultFileIO()
                               {
                                   @Override public void write(FileOperator.RaplaWriter writer, URI storageURL) throws IOException
                                   {
                                       //super.write(writer, storageURL);
                                   }

                                   @Override public InputSource getInputSource(URI storageURL) throws IOException
                                   {
                                       return super.getInputSource(storageURL);
                                   }
                               }

        );
    }

    public static String getTestDataFile(String xmlFile)
    {
        return "src/test/resources/" + xmlFile;
    }

    public static ClientFacade createFacadeWithFile(Logger logger, String xmlFile)
    {
        String resolvedPath = getTestDataFile(xmlFile);
        initFileHack();
        DefaultBundleManager bundleManager = new DefaultBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);

        CommandScheduler scheduler = new DefaultScheduler(logger);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);

        Map<String, FunctionFactory> functionFactoryMap = new HashMap<String, FunctionFactory>();
        StandardFunctions functions = new StandardFunctions(raplaLocale);
        functionFactoryMap.put(StandardFunctions.NAMESPACE, functions);

        RaplaDefaultPermissionImpl defaultPermission = new RaplaDefaultPermissionImpl();
        PermissionController permissionController = new PermissionController(Collections.singleton(defaultPermission));
        FileOperator operator = new FileOperator(logger, i18n, raplaLocale, scheduler, functionFactoryMap, resolvedPath,
                DefaultPermissionControllerSupport.getController());
        FacadeImpl facade = new FacadeImpl(i18n, scheduler, logger, permissionController);
        facade.setOperator(operator);
        return facade;
    }

    static Provider<ClientFacade> createFacadeWithRemote(Logger logger, int port)
    {
        final String serverURL = "http://localhost:" + port + "/";

        BasicRaplaHTTPConnector.setServiceEntryPointFactory(new EntryPointFactory()
        {
            @Override public String getEntryPoint(String interfaceName, String relativePath)
            {
                String url = serverURL + "rapla/" + ((relativePath != null) ? relativePath : interfaceName);
                return url;
            }
        });

        DefaultBundleManager bundleManager = new DefaultBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);

        CommandScheduler scheduler = new DefaultScheduler(logger);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);


        Map<String, FunctionFactory> functionFactoryMap = new HashMap<String, FunctionFactory>();
        StandardFunctions functions = new StandardFunctions(raplaLocale);
        functionFactoryMap.put(StandardFunctions.NAMESPACE, functions);
        RaplaDefaultPermissionImpl defaultPermission = new RaplaDefaultPermissionImpl();
        PermissionController permissionController = new PermissionController(Collections.singleton(defaultPermission));
        Provider<ClientFacade> clientFacadeProvider = new Provider<ClientFacade>()
        {
            @Override public ClientFacade get()
            {
                RemoteConnectionInfo connectionInfo = new RemoteConnectionInfo();
                connectionInfo.setServerURL(serverURL);
                //final ConnectInfo connectInfo = new ConnectInfo("homer", "duffs".toCharArray());
                connectionInfo.setReconnectInfo(null);
                BasicRaplaHTTPConnector.CustomConnector customConnector = new MyCustomConnector(connectionInfo, i18n, scheduler);
                RemoteAuthentificationService remoteAuthentificationService = new RemoteAuthentificationService_JavaJsonProxy(customConnector);
                RemoteStorage remoteStorage = new RemoteStorage_JavaJsonProxy(customConnector);
                RemoteOperator remoteOperator = new RemoteOperator(logger, i18n, raplaLocale, scheduler, functionFactoryMap, remoteAuthentificationService,
                        remoteStorage, connectionInfo, DefaultPermissionControllerSupport.getController());
                FacadeImpl facade = new FacadeImpl(i18n, scheduler, logger, permissionController);
                facade.setOperator(remoteOperator);
                return facade;
            }
        };
        return clientFacadeProvider;
    }

    public RaplaTestCase(String name)
    {
        super(name);
        try
        {
            new File("temp").mkdir();
            File testFolder = new File(TEST_FOLDER_NAME);
            System.setProperty("jetty.home", testFolder.getPath());
            testFolder.mkdir();
            IOUtil.copy(TEST_SRC_FOLDER_NAME + "/test.xconf", TEST_FOLDER_NAME + "/test.xconf");
            //IOUtil.copy( "test-src/test.xlog", TEST_FOLDER_NAME + "/test.xlog" );
        }
        catch (IOException ex)
        {
            throw new RuntimeException("Can't initialize config-files: " + ex.getMessage());
        }
        try
        {
            Class<?> forName = RaplaTestCase.class.getClassLoader().loadClass("org.slf4j.bridge.SLF4JBridgeHandler");
            forName.getMethod("removeHandlersForRootLogger", new Class[] {}).invoke(null, new Object[] {});
            forName.getMethod("install", new Class[] {}).invoke(null, new Object[] {});
        }
        catch (Exception ex)
        {
            getLogger().warn("Can't install logging bridge  " + ex.getMessage());
            // Todo bootstrap log
        }

    }

    public void copyDataFile(String testFile) throws IOException
    {
        try
        {
            IOUtil.copy(testFile, TEST_FOLDER_NAME + "/test.xml");
        }
        catch (IOException ex)
        {
            throw new IOException("Failed to copy TestFile '" + testFile + "': " + ex.getMessage());
        }
    }

    protected <T> T getService(Class<T> role) throws RaplaException
    {
        return null;
    }

    protected SerializableDateTimeFormat formater()
    {
        return new SerializableDateTimeFormat();
    }

    protected Logger getLogger()
    {
        return logger;
    }

    protected void setUp(String testFile) throws Exception
    {
        ErrorDialog.THROW_ERROR_DIALOG_EXCEPTION = true;

        //        URL configURL = new URL("file:./" + TEST_FOLDER_NAME + "/test.xconf");
        //env.setConfigURL( configURL);
        copyDataFile(TEST_SRC_FOLDER_NAME + "/" + testFile);
        raplaContainer = null;// FIXME new RaplaClientServiceImpl( env );
        assertNotNull("Container not initialized.", raplaContainer);
        ClientFacade facade = getFacade();
        facade.login("homer", "duffs".toCharArray());
    }

    @Before protected void setUp() throws Exception
    {
        setUp("testdefault.xml");
    }

    protected RaplaClientServiceImpl getClientService() throws RaplaException
    {
        RaplaClientServiceImpl clientContainer = raplaContainer;
        if (!clientContainer.isRunning())
        {
            try
            {
                clientContainer.start(new ConnectInfo("homer", "duffs".toCharArray()));
            }
            catch (Exception e)
            {
                throw new RaplaException(e.getMessage(), e);
            }
        }
        return raplaContainer;
    }

    protected ClientFacade getFacade() throws RaplaException
    {
        return getService(ClientFacade.class);
    }

    protected RaplaLocale getRaplaLocale() throws RaplaException
    {
        return getService(RaplaLocale.class);
    }

    protected void tearDown() throws Exception
    {
        if (raplaContainer != null)
            raplaContainer.dispose();
    }

}
