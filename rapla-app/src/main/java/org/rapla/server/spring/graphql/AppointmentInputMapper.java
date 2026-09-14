package org.rapla.server.spring.graphql;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.rapla.client.edit.reservation.RepeatingRuleModel;
import org.rapla.client.edit.reservation.RepeatingRuleProjector;
import org.rapla.client.edit.reservation.RepeatingRuleWriter;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.server.spring.graphql.ReservationMutationController.ReservationMutationException;

/**
 * Shared AppointmentInput mapping for the reservation mutation controller
 * AND the availability queries (PRD 091 Phase 4.5 — the availability path
 * previously rejected {@code repeating} as UNSUPPORTED because only the
 * mutation controller could materialize it; extracting the one
 * materializer keeps a single recurrence code path).
 *
 * <p>PRD 091 Phase 2.0 — full-state round-trip safety: materialize the
 * repeating rule from the input instead of silently flattening the series.
 * Reuses the PRD 023/024 pure-Java rule model — no parallel recurrence
 * logic.
 */
final class AppointmentInputMapper
{
    private AppointmentInputMapper()
    {
    }

    @SuppressWarnings("unchecked")
    static void applyRepeating(AppointmentImpl a, Map<String, Object> ai, String path)
    {
        Map<String, Object> rep = (Map<String, Object>) ai.get("repeating");
        if (rep == null) return;

        RepeatingType type;
        try
        {
            type = RepeatingType.valueOf(String.valueOf(rep.get("type")));
        }
        catch (IllegalArgumentException e)
        {
            throw new ReservationMutationException("INVALID_VALUE", path + ".repeating.type",
                    "unknown repeating type " + rep.get("type"));
        }
        int interval = rep.get("interval") instanceof Number n ? n.intValue() : 1;
        LocalDate end = (LocalDate) rep.get("end");
        Integer count = rep.get("count") instanceof Number n ? n.intValue() : null;
        Set<Integer> weekdays = rep.get("weekdays") instanceof List<?> wd
                ? wd.stream().map(w -> ((Number) w).intValue()).collect(Collectors.toSet())
                : null;

        RepeatingRuleProjector.EndingMode mode = end != null
                ? RepeatingRuleProjector.EndingMode.UNTIL
                : count != null ? RepeatingRuleProjector.EndingMode.N_TIMES
                                : RepeatingRuleProjector.EndingMode.FOREVER;

        a.setRepeatingEnabled(true);
        RepeatingRuleModel model = new RepeatingRuleModel(type, interval, weekdays, mode,
                end != null ? end.atStartOfDay() : null, count != null ? count : 0);
        RepeatingRuleWriter.writeTo(model, a.getRepeating(), a.getStart());

        List<LocalDate> exceptions = (List<LocalDate>) rep.get("exceptions");
        if (exceptions != null)
        {
            Repeating repeating = a.getRepeating();
            for (LocalDate ex : exceptions)
            {
                if (ex != null) repeating.addException(ex.atStartOfDay());
            }
        }
    }
}
