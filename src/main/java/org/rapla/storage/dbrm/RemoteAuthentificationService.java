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

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.VoidResult;

@Path("authentification")
public interface RemoteAuthentificationService
{
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    FutureResult<LoginTokens> login(@QueryParam("username") String username,String password,@QueryParam("connectAs") String connectAs);
	
	/** same as login but passes the login info into a LoginCredentials Object*/
    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    FutureResult<LoginTokens> auth(LoginCredentials credentials);
	
    @GET
    @Path("destroy")
	FutureResult<VoidResult> logout();
	
    @GET
    @Path("refreshToken")
    FutureResult<String> getRefreshToken();
	
    @GET
    @Path("regenerateRefreshToken")
    FutureResult<String> regenerateRefreshToken();

    @GET
    @Path("loginToken")
    FutureResult<LoginTokens> refresh(@QueryParam("refreshToken") String refreshToken);

}
