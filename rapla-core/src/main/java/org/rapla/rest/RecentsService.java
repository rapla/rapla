package org.rapla.rest;

import org.rapla.framework.RaplaException;
import org.rapla.rest.dto.UserListEntryRequest;
import org.rapla.rest.dto.UserListItem;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * REST contract for {@code /api/recents} (PRD 089). Single source of truth for
 * routing; {@code UserListsController} implements it. Recents are the caller's
 * recently-acted-on resources/users, capped at 20, newest first.
 *
 * <p>Reads are side-effect-free (§16) — live-resolve + §12-filter. Writes
 * ({@code POST}/{@code DELETE}) additionally prune dangling ids (D6).
 */
@HttpExchange("/api/recents")
public interface RecentsService
{
    @GetExchange
    List<UserListItem> getRecents() throws RaplaException;

    @PostExchange
    List<UserListItem> addRecent(@RequestBody UserListEntryRequest body) throws RaplaException;

    @DeleteExchange
    List<UserListItem> clearRecents() throws RaplaException;
}
