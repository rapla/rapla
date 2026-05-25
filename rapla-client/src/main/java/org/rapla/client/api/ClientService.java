/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.client.api;

import org.rapla.ConnectInfo;
import org.rapla.framework.Disposable;

/** This service starts and manages the rapla-gui-client.
 */
public interface ClientService extends Disposable
{
    void start(ConnectInfo connectInfo) throws Exception;
    void addRaplaClientListener(RaplaClientListener listener);

    /**
     * PRD 029 Phase 5 — apply an impersonation override on top of the
     * already-started session. Called by the launcher
     * ({@code SpringRaplaClient.main}) immediately after
     * {@link #start(ConnectInfo)} on the iteration where admin switched to
     * another user. The session's primary tokens stay admin's (so refresh
     * and impersonation renewal keep working); outbound requests use the
     * impersonation access token via the dual-slot model in
     * {@code RemoteConnectionInfo.getEffectiveAccessToken()}.
     *
     * <p>Angular equivalent: {@code AuthService.impersonate(targetUsername)}
     * (but that one mints the token too; this method just stashes a
     * pre-minted one, because the rapla server already issued it before the
     * context close+recreate).
     *
     * @param impersonationAccessToken the rapla-SAS-signed access JWT (no
     *   refresh counterpart by design — PRD 051). Null/empty no-ops.
     * @param targetUsername the impersonated user's username
     */
    default void setImpersonation(String impersonationAccessToken, String targetUsername)
    {
        // default no-op so test doubles / standalone impls don't need to override
    }
}
