package org.rapla.plugin.exchangeconnector.server;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.RemoteSession;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import java.util.Collection;

@DefaultImplementation(context=InjectionContext.server, of=ExchangeConnectorRemote.class)
public class ExchangeConnectorRemoteObjectFactory implements ExchangeConnectorRemote
{
	
    @Inject
    SynchronisationManager manager;
    @Inject
    Logger logger;
	@Inject
	RemoteSession session;
    private final HttpServletRequest request;	
	
	@Inject
	public ExchangeConnectorRemoteObjectFactory(@Context HttpServletRequest request) {
        this.request = request;
	}
	
	protected Logger getLogger()
	{
	    return logger;
	}

    @Override
    public SynchronizationStatus getSynchronizationStatus() throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        return manager.getSynchronizationStatus( user);
    }

    @Override
    public void synchronize(String mailbox) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        // Synchronize this user after registering
        getLogger().debug("Invoked change sync for user " + user.getUsername());
        manager.synchronizeUser(user, mailbox);
    }

    @Override
    public Collection<String> changeUser(String exchangeUsername, String exchangePassword) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        String raplaUsername = user.getUsername();
        getLogger().info("Invoked add exchange user for rapla " + raplaUsername + " with exchange user " + exchangeUsername);
        Collection<String> sharedMailboxes = manager.changeUser(exchangeUsername, exchangePassword, user);
        return sharedMailboxes;
    }

    @Override
    public void removeUser() throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        getLogger().info("Removing exchange connection for user " + user);
        manager.removeTasksAndExports(user);
    }

    @Override
    public Collection<String> refreshMailboxes() throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        return manager.refreshMailboxes(user);
    }
	

}

//public String completeReconciliation() throws RaplaException {
//String returnMessage = "Disabled!";
//String returnMessage = "Sync all encountered a problem";
//try {
//	scheduledDownloadTimer.cancel();
//	Thread completeReconciliationThread = new Thread(new CompleteReconciliationHandler(clientFacade), "CompleteReconciliationThread");
//	completeReconciliationThread.start();
//	while (completeReconciliationThread.isAlive()) {
//		this.wait(100);
//	}
//	initScheduledDownloadTimer();
//	returnMessage =  "Synchronization of all  items is finished";
//} catch (InterruptedException e) {
//	logException(e);
//}
//return returnMessage;
//}



//public void setDownloadFromExchange( boolean downloadFromExchange) throws RaplaException {
//String raplaUsername = user.getUsername();
//final boolean result = accountStorage.setDownloadFromExchange(raplaUsername, downloadFromExchange);
//if (result)
//    accountStorage.save();
////todo: synch user if new?
//}