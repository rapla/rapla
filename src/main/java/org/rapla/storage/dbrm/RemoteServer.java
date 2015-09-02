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

import java.util.Map;
import java.util.Set;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.rapla.components.i18n.LocalePackage;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.gwtjsonrpc.common.ResultType;
import org.rapla.gwtjsonrpc.common.VoidResult;

@WebService
public interface RemoteServer extends RemoteJsonService {
	@ResultType(LoginTokens.class)
	FutureResult<LoginTokens> login(@WebParam(name="username") String username,@WebParam(name="password") String password,@WebParam(name="connectAs") String connectAs);
	
	/** same as login but passes the login info into a LoginCredentials Object*/
	@ResultType(LoginTokens.class)
    FutureResult<LoginTokens> auth(@WebParam(name="credentials") LoginCredentials credentials);
	
	@ResultType(VoidResult.class)
	FutureResult<VoidResult> logout();
	
	@ResultType(String.class)
    FutureResult<String> getRefreshToken();
	
	@ResultType(String.class)
    FutureResult<String> regenerateRefreshToken();
	
	@ResultType(LoginTokens.class)
    FutureResult<LoginTokens> refresh(@WebParam(name="refreshToken") String refreshToken);
	
	@ResultType(LocalePackage.class)
	FutureResult<LocalePackage> locale(@WebParam(name="id")String id, @WebParam(name="locale") String locale);

	@ResultType(Map.class)
	FutureResult<Map<String, Set<String>>> countries(Set<String> languages);
		
}
