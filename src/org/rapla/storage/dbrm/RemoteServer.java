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

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.framework.RaplaException;

import com.google.gwtjsonrpc.common.FutureResult;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.common.VoidResult;

@WebService
public interface RemoteServer extends RemoteJsonService {
	FutureResult<String> login(@WebParam(name="username") String username,@WebParam(name="password") String password,@WebParam(name="connectAs") String connectAs) throws RaplaException;
	FutureResult<VoidResult> logout() throws RaplaException;
}
