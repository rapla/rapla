package org.rapla.server.internal;

import org.rapla.entities.User;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.logger.Logger;
import org.rapla.server.RemoteSession;

/** Implementation of RemoteStorage as a RemoteService
 * @see org.rapla.storage.dbrm.RemoteStorage
 */
public class RemoteSessionImpl implements RemoteSession {
    /**
     *
     */
    User user;
    Logger logger;
   // private String accessToken;
    
    public RemoteSessionImpl(Logger logger, User user) {
        this.logger = logger;
        this.user = user;
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

//    public void setAccessToken( String token)
//    {
//        this.accessToken = token;
//    }
//    
//    @Override
//    public String getAccessToken() {
//        return accessToken;
//    }

    public void logout() {
    	user = null;
    }



}