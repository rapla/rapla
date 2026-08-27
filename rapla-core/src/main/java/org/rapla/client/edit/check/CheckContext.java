package org.rapla.client.edit.check;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import org.rapla.entities.User;
import org.rapla.entities.domain.Reservation;

/**
 * PRD 105 — everything a {@link ReservationChecker} may look at. DATA only: a checker's own
 * collaborators (operator, period model, …) arrive through its constructor, so the context stays
 * trivially constructible in a tier-1 test.
 *
 * @param reservation          the reservation about to be saved — transient for a draft check,
 *                             already-stored for a re-check; never null
 * @param caller               whose permissions and preferences apply; never null
 * @param locale               for name/summary rendering inside warning args
 * @param scopeAllocatableIds  D3 — the caller's current view scope (SPA filter chips). EMPTY means
 *                             "no scope stated", which disables the {@code NOT_IN_CALENDAR} check
 *                             rather than failing it: an absent scope is not an empty scope.
 * @param enabledCodes         the codes the caller has switched on (`CalendarOptionsImpl`
 *                             preferences); a checker may skip work whose code is disabled, and the
 *                             service filters the result again so a checker cannot leak a disabled code
 */
public record CheckContext(Reservation reservation, User caller, Locale locale,
        Set<String> scopeAllocatableIds, Set<ReservationWarning.Code> enabledCodes)
{
    public boolean isEnabled(ReservationWarning.Code code)
    {
        return enabledCodes == null || enabledCodes.contains(code);
    }

    /** The reservation as the single-element collection the pure rule functions take. */
    public Collection<Reservation> asCollection()
    {
        return java.util.List.of(reservation);
    }
}
