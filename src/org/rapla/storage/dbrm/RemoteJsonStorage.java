/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 ?, Christopher Kohlhaas                               |
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
package org.rapla.storage.dbrm;

import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;

import com.google.gwtjsonrpc.common.AllowCrossSiteRequest;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.RemoteJsonService;
@WebService
public interface RemoteJsonStorage extends RemoteJsonService{
	@AllowCrossSiteRequest
	void getUser(@WebParam(name="username")String username,AsyncCallback<UserImpl> callback);
	@AllowCrossSiteRequest
	void storeUser(UserImpl user,AsyncCallback<Boolean> callback);
	@AllowCrossSiteRequest
	void getResources(AsyncCallback<List<AllocatableImpl>> callback);
	@AllowCrossSiteRequest
	void getCategory(AsyncCallback<CategoryImpl> callback);
}
