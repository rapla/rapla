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

/**
 * Client-side username/password authentication seam.
 *
 * <p>PRD 041 removed the rapla-custom {@code POST /api/auth/login} and
 * {@code /api/auth/refresh} endpoints (deleted together with the server's
 * {@code AuthController}). The implementation wired in {@code ClientProxyConfig}
 * — {@code OAuth2RemoteAuthentificationService} — now talks to the OAuth 2.0
 * token endpoint ({@code POST /oauth2/token} with {@code grant_type=password} /
 * {@code grant_type=refresh_token}, RFC 6749). This interface is the seam the
 * Swing fallback login dialog and {@code MyCustomConnector}'s password-reauth
 * path call; both are unaffected by the wire change. See
 * {@code docs/authentication.md} → "Client login flows".</p>
 *
 * <p>Note: the OAuth2 password grant has no {@code connectAs} parameter —
 * impersonation ("{@code user su other}") is rejected, not silently ignored.</p>
 */
public interface RemoteAuthentificationService
{
    LoginTokens login(LoginCredentials credentials) throws RaplaException;

    LoginTokens refresh(RefreshRequest body) throws RaplaException;

    /**
     * Refresh-token request body for {@link #refresh}.
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
