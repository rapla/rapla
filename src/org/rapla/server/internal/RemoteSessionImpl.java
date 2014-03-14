package org.rapla.server.internal;

import org.rapla.entities.User;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.server.RemoteSession;

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
    
    public RemoteSessionImpl(RaplaContext context, String clientName) {
        super( context );
        logger = super.getLogger().getChildLogger(clientName);
    }

    public Logger getLogger() {
    	return logger;
    }

    public User getUser() throws RaplaContextException {
    	if (user == null)
    	    throw new RaplaContextException("No user found in session.");
    	return user;
    }

    public boolean isAuthentified() {
        return user != null;
    }

    public void setUser( User user)
    {
        this.user = user;
    }

    public abstract void logout() throws RaplaException;



}