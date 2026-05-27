/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.storage.PermissionController;
import org.rapla.storage.dbrm.UserMe;
import org.rapla.storage.dbrm.UserSummary;
import org.rapla.storage.dbrm.UsersService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PRD 051 — admin-scope user listing for the SPA's "Switch to user"
 * dialog. Returns {@link UserSummary} DTOs only — narrow wire shape
 * per AGENTS.md §12 (no email, no group list, no preferences).
 *
 * <p>Server-side filter: iterate {@code facade.getUsers()}, keep
 * those passing {@link PermissionController#canAdminUser(User, User)}
 * against the calling user. Includes the caller themselves
 * (self-impersonation is a no-op but harmless and the dropdown stays
 * uniform).
 *
 * <p>An empty response is the "caller has no admin authority" signal
 * the SPA toolbar uses to decide whether to make the username chip
 * clickable.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class UsersController implements UsersService
{
    private final RemoteSession session;
    private final RaplaFacade facade;
    private final HttpServletRequest request;

    public UsersController(RemoteSession session, RaplaFacade facade, HttpServletRequest request)
    {
        this.session = session;
        this.facade = facade;
        this.request = request;
    }

    @Override
    public List<UserSummary> list() throws RaplaException
    {
        User caller = session.checkAndGetUser(request);
        List<UserSummary> result = new ArrayList<>();
        for (User candidate : facade.getUsers())
        {
            if (PermissionController.canAdminUser(caller, candidate))
            {
                result.add(new UserSummary(candidate.getUsername(), nonNullName(candidate)));
            }
        }
        // Stable order — dropdown picker UX. Alphabetical by username.
        result.sort(Comparator.comparing(UserSummary::getUsername,
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Override
    public UserMe me() throws RaplaException
    {
        User caller = session.checkAndGetUser(request);
        return new UserMe(caller.getId(), caller.getUsername(), nonNullName(caller));
    }

    private static String nonNullName(User u)
    {
        String name = u.getName();
        return name == null ? "" : name;
    }
}
