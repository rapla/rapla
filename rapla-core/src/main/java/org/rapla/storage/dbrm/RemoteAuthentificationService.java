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

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/authentication")
public interface RemoteAuthentificationService
{
    @PostExchange
    LoginTokens login(@RequestParam("username") String username,
                      @RequestBody String password,
                      @RequestParam(value = "connectAs", required = false) String connectAs) throws RaplaException;

    @GetExchange("/destroy")
    void logout() throws RaplaException;

    @GetExchange("/refreshToken")
    String getRefreshToken() throws RaplaException;

    @GetExchange("/regenerateRefreshToken")
    String regenerateRefreshToken() throws RaplaException;

    @GetExchange("/loginToken")
    LoginTokens refresh(@RequestParam("refreshToken") String refreshToken) throws RaplaException;
}
