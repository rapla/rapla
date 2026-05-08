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
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Client-side proxy for the server's {@code AuthController} (Spring Boot REST).
 * Aligned with the post-PRD-001-Phase-3 server API ({@code POST /auth/login} +
 * {@code POST /auth/refresh}, JSON body, {@code TokenResponse} return shape).
 *
 * <p>Pre-Spring-Boot wire format (`/authentication` path, query-param username,
 * raw-body password, GET-style logout/refresh) was retired with this change.
 * Logout is no longer a server round-trip — JWT access tokens are stateless,
 * so client-side disconnect just drops the token reference.</p>
 */
@HttpExchange("/auth")
public interface RemoteAuthentificationService
{
    @PostExchange("/login")
    LoginTokens login(@RequestBody LoginCredentials credentials) throws RaplaException;

    @PostExchange("/refresh")
    LoginTokens refresh(@RequestBody RefreshRequest body) throws RaplaException;

    /**
     * Refresh-token request body for {@link #refresh}.
     * Server (`AuthController.RefreshRequest`) expects {@code {"refreshToken": "..."}}.
     */
    final class RefreshRequest
    {
        public String refreshToken;

        public RefreshRequest() {}

        public RefreshRequest(String refreshToken)
        {
            this.refreshToken = refreshToken;
        }
    }
}
