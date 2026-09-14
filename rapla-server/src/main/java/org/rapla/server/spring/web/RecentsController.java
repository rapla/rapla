package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.rest.RecentsService;
import org.rapla.rest.dto.UserListEntryRequest;
import org.rapla.rest.dto.UserListItem;
import org.rapla.server.RemoteSession;
import org.rapla.server.spring.UserListsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Per-user recents REST surface (PRD 089 Phase 1). Implements the
 * {@link RecentsService} {@code @HttpExchange} contract (PRD 049); routing
 * metadata lives on the interface. Delegates storage + §12-filtering to
 * {@link UserListsService}.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class RecentsController implements RecentsService
{
    private final UserListsService userLists;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public RecentsController(UserListsService userLists, RemoteSession session, HttpServletRequest request)
    {
        this.userLists = userLists;
        this.session = session;
        this.request = request;
    }

    @Override
    public List<UserListItem> getRecents() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return userLists.readRecents(user);
    }

    @Override
    public List<UserListItem> addRecent(UserListEntryRequest body) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return userLists.addRecent(user, body.id(), body.kind());
    }

    @Override
    public List<UserListItem> clearRecents() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return userLists.clearRecents(user);
    }
}
