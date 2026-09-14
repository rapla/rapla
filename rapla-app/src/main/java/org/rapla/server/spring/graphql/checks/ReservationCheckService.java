package org.rapla.server.spring.graphql.checks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.rapla.client.edit.check.CheckContext;
import org.rapla.client.edit.check.ReservationChecker;
import org.rapla.client.edit.check.ReservationWarning;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PRD 105 — folds the registered {@link ReservationChecker}s over one reservation.
 *
 * <p>The checker list is injected, so ordering follows {@code @Order} and a plugin
 * (dhbwrapla) contributes a check by publishing another bean — no change here. Swing's
 * {@code EventCheck} chain is the model; this is its second caller, not its replacement (D6).
 *
 * <p>Per-code enablement comes from the CALLER's existing {@code CalendarOptionsImpl}
 * preferences — the same keys Swing's {@code WarningsOption} page writes, so a warning switched
 * off there is off here (D4/D8). The filter runs twice on purpose: checkers may skip disabled
 * work, and the service drops anything a checker still returned.
 */
@Service
public class ReservationCheckService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationCheckService.class);

    private final List<ReservationChecker> checkers;
    private final StorageOperator operator;

    public ReservationCheckService(List<ReservationChecker> checkers, StorageOperator operator)
    {
        this.checkers = checkers;
        this.operator = operator;
    }

    public List<ReservationWarning> check(Reservation reservation, User caller, Locale locale,
            Set<String> scopeAllocatableIds)
    {
        Set<ReservationWarning.Code> enabled = enabledCodes(caller);
        CheckContext context = new CheckContext(reservation, caller, locale,
                scopeAllocatableIds == null ? Set.of() : scopeAllocatableIds, enabled);

        List<ReservationWarning> out = new ArrayList<>();
        for (ReservationChecker checker : checkers)
        {
            List<ReservationWarning> warnings;
            try
            {
                warnings = checker.check(context);
            }
            catch (RuntimeException e)
            {
                // One broken checker must not swallow the others' findings — the caller is about to
                // save, and a partial warning list beats none. Logged loudly, never surfaced (a
                // checker's failure mode is not the user's business).
                LOGGER.error("Reservation checker {} failed", checker.getClass().getName(), e);
                continue;
            }
            if (warnings == null) continue;
            for (ReservationWarning w : warnings)
            {
                if (enabled.contains(w.code())) out.add(w);
            }
        }
        return out;
    }

    /**
     * The codes this caller wants. Only four of the seven have a preference switch in
     * {@code CalendarOptionsImpl}; the rest are always on (Swing has no checkbox for them either).
     */
    private Set<ReservationWarning.Code> enabledCodes(User caller)
    {
        Set<ReservationWarning.Code> enabled = EnumSet.allOf(ReservationWarning.Code.class);
        Preferences preferences;
        try
        {
            preferences = operator.getPreferences(caller, false);
        }
        catch (RaplaException e)
        {
            LOGGER.warn("Could not read warning preferences for {} — all checks stay on", caller, e);
            return enabled;
        }
        if (preferences == null) return enabled;
        if (!preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_CONFLICT_WARNING, true))
        {
            enabled.remove(ReservationWarning.Code.CONFLICT);
        }
        if (!preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING, true))
        {
            enabled.remove(ReservationWarning.Code.NOT_IN_CALENDAR);
        }
        if (!preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_HOLIDAY_WARNING, true))
        {
            enabled.remove(ReservationWarning.Code.HOLIDAY_ON_APPOINTMENT);
        }
        return enabled;
    }
}
