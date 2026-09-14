package org.rapla.rest;

import org.rapla.framework.RaplaException;
import org.rapla.rest.dto.UserListEntryRequest;
import org.rapla.rest.dto.UserListItem;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * REST contract for {@code /api/favorites} (PRD 089). Single source of truth for
 * routing; {@code UserListsController} implements it. Favorites are the caller's
 * pinned resources/users, an ordered set with no cap.
 *
 * <p>Reads are side-effect-free (§16) — live-resolve + §12-filter. Writes
 * ({@code POST}/{@code DELETE}) additionally prune dangling ids (D6).
 */
@HttpExchange("/api/favorites")
public interface FavoritesService
{
    @GetExchange
    List<UserListItem> getFavorites() throws RaplaException;

    @PostExchange
    List<UserListItem> addFavorite(@RequestBody UserListEntryRequest body) throws RaplaException;

    @DeleteExchange("/{id}")
    List<UserListItem> removeFavorite(@PathVariable("id") String id) throws RaplaException;
}
