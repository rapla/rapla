package org.rapla;

import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.test.util.RaplaTestCase;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

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
        RaplaTestCase.ServerContext context = RaplaTestCase.createServerContext(logger, "/testdefault.xml", port);
        serviceContainer = (ServerServiceImpl) context.getServiceContainer();
        this.server = context.getServer();
        clientFacadeProvider = RaplaTestCase.createFacadeWithRemote(logger, port);
    }

    public boolean login(ClientFacade facade, String username,char[] password) throws RaplaException {
        return facade.login(username,password);
    }

    public  void logout(ClientFacade facade) throws RaplaException {
        facade.logout();
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

    protected Reservation newReservation(ClientFacade  clientFacade) throws RaplaException
    {
        RaplaFacade facade = clientFacade.getRaplaFacade();
        User user = clientFacade.getUser();
        final DynamicType[] eventTypes = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        Classification classification = eventTypes[0].newClassification();
        return facade.newReservation(classification, user);
    }

    protected Allocatable newResource(ClientFacade  clientFacade) throws RaplaException
    {
        RaplaFacade facade = clientFacade.getRaplaFacade();
        User user = clientFacade.getUser();
        final DynamicType[] eventTypes = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        Classification classification = eventTypes[0].newClassification();
        return facade.newAllocatable(classification, user);
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
