package org.rapla;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Provider;

import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.test.util.RaplaTestCase;

@RunWith(JUnit4.class)
public abstract class AbstractTestWithServer
{

    private List<ClientFacade> createdClientFacades;
    private Server server;
    private Provider<ClientFacade> clientFacadeProvider;
    private Logger logger;
    private ServerServiceImpl serviceContainer;
    private int port = 8052;

    @Before
    public void createServerAndSetup() throws Exception
    {
        createdClientFacades = new ArrayList<>();
        logger = RaplaTestCase.initLoger();
        RaplaTestCase.ServerContext context = RaplaTestCase.createServerContext(logger, "testdefault.xml", port);
        serviceContainer = (ServerServiceImpl) context.getServiceContainer();
        this.server = context.getServer();
        clientFacadeProvider = RaplaTestCase.createFacadeWithRemote(logger, port);
    }

    @After
    public void shutdownServerAndDisposeFacades() throws Exception
    {
        RaplaTestCase.dispose(serviceContainer.getFacade());
        for (ClientFacade clientFacade : createdClientFacades)
        {
            RaplaTestCase.dispose(clientFacade.getRaplaFacade());
        }
        server.stop();
    }

    protected RaplaFacade getServerRaplaFacade()
    {
        return serviceContainer.getFacade();
    }

    protected int getPort()
    {
        return port;
    }

    protected Logger getLogger()
    {
        return logger;
    }

    protected ClientFacade createClientFacade()
    {
        final ClientFacade clientFacade = clientFacadeProvider.get();
        createdClientFacades.add(clientFacade);
        return clientFacade;
    }

}
