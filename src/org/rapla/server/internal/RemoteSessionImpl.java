package org.rapla.server.internal;

import org.rapla.entities.User;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.logger.Logger;
import org.rapla.server.RemoteSession;

/** Implementation of RemoteStorage as a RemoteService
 * @see org.rapla.storage.dbrm.RemoteStorage
 */
public class RemoteSessionImpl extends RaplaComponent implements RemoteSession {
    /**
     *
     */
    User user;
    Logger logger;
    private String accessToken;
    
    public RemoteSessionImpl(RaplaContext context, String clientName) {
        super( context );
        logger = super.getLogger().getChildLogger(clientName);
    }

    public Logger getLogger() {
    	return logger;
    }

    @Override
    public User getUser() throws RaplaContextException {
    	if (user == null)
    	    throw new RaplaContextException("No user found in session.");
    	return user;
    }
    @Override
    public boolean isAuthentified() {
        return user != null;
    }

    public void setUser( User user)
    {
        this.user = user;
    }
    
    public void setAccessToken( String token)
    {
        this.accessToken = token;
    }
    
    @Override
    public String getAccessToken() {
        return accessToken;
    }

    public void logout() {
    	this.setUser( null);
    }



}