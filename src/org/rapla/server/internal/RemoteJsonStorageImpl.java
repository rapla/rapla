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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.rapla.components.util.Cancelable;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.entities.internal.UserImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.RemoteJsonFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.dbrm.RemoteJsonStorage;

import com.google.gwtjsonrpc.common.AsyncCallback;

/** Provides an adapter for each client-session to their shared storage operator
 * Handles security and synchronizing aspects.
 */
public class RemoteJsonStorageImpl implements RemoteJsonFactory<RemoteJsonStorage> {
    CachableStorageOperator operator;
    
    protected SecurityManager security;
    
   // RemoteServer server;
    RaplaContext context;
    
    long repositoryVersion = 0;
    long cleanupPointVersion = 0;
        
    protected AuthenticationStore authenticationStore;
    Logger logger;
    ClientFacade facade;
    RaplaLocale raplaLocale;
    CommandScheduler commandQueue;
    Cancelable scheduledCleanup;
    
    public RemoteJsonStorageImpl(RaplaContext context) throws RaplaException {
        this.context = context;
        this.logger = context.lookup( Logger.class);
        commandQueue = context.lookup( CommandScheduler.class);
        facade = context.lookup( ClientFacade.class);
        raplaLocale = context.lookup( RaplaLocale.class);
        operator = (CachableStorageOperator)facade.getOperator();
        security = context.lookup( SecurityManager.class);
    }
    
    public Logger getLogger() {
        return logger;
    }

    
    public I18nBundle getI18n() throws RaplaException {
    	return context.lookup(RaplaComponent.RAPLA_RESOURCES);
    }



    @Override
    public RemoteJsonStorage createService(final RemoteSession session) {
        return new RemoteJsonStorage() {
			@Override
			public void getUsers(String username, AsyncCallback<UserImpl[]> callback) {
				try {
					Collection<User> users = operator.getObjects(User.class);
					ArrayList<UserImpl> result = new ArrayList<UserImpl>();
					for (User user: users)
					{
						if ( username == null || user.getUsername().equalsIgnoreCase(username))
						{
							result.add( (UserImpl) user );
						}
					}
					callback.onSuccess(result.toArray( new UserImpl[] {}));
				} catch (RaplaException e) {
					callback.onFailure( e );
				}
				
			}
		};
    }

}

