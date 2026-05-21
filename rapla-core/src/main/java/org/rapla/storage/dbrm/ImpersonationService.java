/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Admin "switch to user" — PRD 051. Single endpoint that mints (or
 * renews) a rapla-SAS-signed access token for the impersonated target,
 * carrying an {@code act} claim that names the admin actor. Same wire
 * shape for the initial call and every renewal; the endpoint has no
 * server-side per-impersonation state.
 *
 * <p>Authorization: the caller must hold a Bearer token (any issuer
 * rapla accepts) that resolves to a rapla {@link org.rapla.entities.User}
 * who passes {@code PermissionController.canAdminUser(actor, target)}
 * — i.e. global admin OR group-admin whose
 * {@code can_admin_parent} scope includes the target's groups. The
 * check runs server-side on every call; if the admin loses
 * group-admin status mid-session, the next renewal fails with 403.
 *
 * <p>No {@code refresh_token} is issued. Tokens are short-lived (1 h
 * per {@code access-token-time-to-live}); renewal happens by calling
 * this endpoint again with the admin's current Bearer. Eliminates
 * server-side state for the impersonation session and removes the
 * long-lived impersonation credential from client disk.
 */
@HttpExchange("/api/auth/impersonate")
public interface ImpersonationService
{
    @PostExchange
    ImpersonationResponse impersonate(@RequestParam("target_username") String targetUsername)
            throws RaplaException;
}
