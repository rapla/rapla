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

import jsinterop.annotations.JsType;
import org.rapla.framework.RaplaException;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("authentication")
@JsType
public interface RemoteAuthentificationService
{
    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    LoginTokens login(@QueryParam("username") String username, String password, @QueryParam("connectAs") String connectAs) throws RaplaException;

    @GET
    @Path("destroy")
    void logout() throws RaplaException;

    @GET
    @Path("refreshToken")
    String getRefreshToken() throws RaplaException;

    @GET
    @Path("regenerateRefreshToken")
    String regenerateRefreshToken() throws RaplaException;

    @GET
    @Path("loginToken")
    LoginTokens refresh(@QueryParam("refreshToken") String refreshToken) throws RaplaException;

}
