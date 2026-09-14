package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.rest.FavoritesService;
import org.rapla.rest.dto.UserListEntryRequest;
import org.rapla.rest.dto.UserListItem;
import org.rapla.server.RemoteSession;
import org.rapla.server.spring.UserListsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Per-user favorites REST surface (PRD 089 Phase 1). Implements the
 * {@link FavoritesService} {@code @HttpExchange} contract (PRD 049); routing
 * metadata lives on the interface. Delegates storage + §12-filtering to
 * {@link UserListsService}.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class FavoritesController implements FavoritesService
{
    private final UserListsService userLists;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public FavoritesController(UserListsService userLists, RemoteSession session, HttpServletRequest request)
    {
        this.userLists = userLists;
        this.session = session;
        this.request = request;
    }

    @Override
    public List<UserListItem> getFavorites() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return userLists.readFavorites(user);
    }

    @Override
    public List<UserListItem> addFavorite(UserListEntryRequest body) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return userLists.addFavorite(user, body.id(), body.kind());
    }

    @Override
    public List<UserListItem> removeFavorite(String id) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return userLists.removeFavorite(user, id);
    }
}
