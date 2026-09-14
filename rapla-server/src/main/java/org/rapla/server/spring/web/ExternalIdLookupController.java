package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;
import org.rapla.rest.ExternalIdLookup;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves {@code externalid} annotations through the operator's
 *  cache — no date window and no permission-blind index, unlike a client-side scan
 *  over queried events. */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class ExternalIdLookupController implements ExternalIdLookup
{
    private final CachableStorageOperator operator;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public ExternalIdLookupController(CachableStorageOperator operator, RemoteSession session, HttpServletRequest request)
    {
        this.operator = operator;
        this.session = session;
        this.request = request;
    }

    @Override
    public Map<String, List<String>> resolve(List<String> externalIds) throws RaplaException
    {
        final User user = session.checkAndGetUser(request);
        final PermissionController permissionController = operator.getPermissionController();
        final Set<String> wanted = new HashSet<>(externalIds);
        wanted.remove(null);
        final Map<String, List<String>> result = new LinkedHashMap<>();
        if (wanted.isEmpty())
        {
            return result;
        }
        // ponytail: one pass over every reservation in the cache. The operator's
        // externalIds index would be O(1) but keeps a single reference per id, which
        // loses the duplicates this data is full of. Revisit if the scan shows up in
        // a profile — the caller is an admin dialog, not a hot path.
        for (Reservation reservation : operator.getReservations())
        {
            final String externalId = reservation.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
            if (externalId == null || !wanted.contains(externalId))
            {
                continue;
            }
            // Template reservations sit in the same cache and can carry an external id
            // (a template built from an imported event keeps the annotation). They are not
            // events and never show up in a normal query — skip them, or an import would
            // mistake the template for the seminar it is supposed to create.
            if (reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE) != null)
            {
                continue;
            }
            if (permissionController.canRead(reservation, user))
            {
                result.computeIfAbsent(externalId, ( key ) -> new ArrayList<>()).add(reservation.getId());
            }
        }
        return result;
    }

}
