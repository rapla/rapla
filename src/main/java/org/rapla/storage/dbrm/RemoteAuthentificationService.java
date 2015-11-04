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

import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.jsonrpc.common.VoidResult;

@RemoteJsonMethod
public interface RemoteAuthentificationService
{
    FutureResult<LoginTokens> login(@WebParam(name="username") String username,@WebParam(name="password") String password,@WebParam(name="connectAs") String connectAs);
	
	/** same as login but passes the login info into a LoginCredentials Object*/
    FutureResult<LoginTokens> auth(@WebParam(name="credentials") LoginCredentials credentials);
	
	FutureResult<VoidResult> logout();
	
    FutureResult<String> getRefreshToken();
	
    FutureResult<String> regenerateRefreshToken();

    FutureResult<LoginTokens> refresh(@WebParam(name="refreshToken") String refreshToken);

}
