package org.rapla.server.internal;

import org.rapla.entities.User;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.server.RemoteSession;
import org.rapla.storage.StorageOperator;

/** Implementation of RemoteStorage as a RemoteService
 * @see org.rapla.storage.dbrm.RemoteStorage
 * @see org.rapla.server.internal.org.rapla.server.rpc.RemoteServiceDispatcher
 *
 */
public abstract class RemoteSessionImpl extends RaplaComponent implements RemoteSession {
    /**
     *
     */
    User user;
    Logger logger;
    StorageOperator operator;
    
    public RemoteSessionImpl(RaplaContext context, String clientName) throws RaplaException {
        super( context );
        operator = context.lookup(StorageOperator.class);
        logger = super.getLogger().getChildLogger(clientName);
    }

    public Logger getLogger() {
    	return logger;
    }

    public User getUser() throws RaplaException {
    	if (user == null)
    	    throw new IllegalStateException("No user found in session.");
    	return user;
    }

    public boolean isAuthentified() {
        return user != null && operator.isConnected();
    }

    public void setUser( User user)
    {
        this.user = user;
    }

    public abstract void logout() throws RaplaException;



}