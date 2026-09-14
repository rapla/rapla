package org.rapla.server.spring.graphql.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.rapla.client.edit.check.CheckContext;
import org.rapla.client.edit.check.DefaultReservationWarnings;
import org.rapla.client.edit.check.HolidayWarningModel;
import org.rapla.client.edit.check.RequestAllocationWarnings;
import org.rapla.client.edit.check.ReservationChecker;
import org.rapla.client.edit.check.ReservationWarning;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.SyncStorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * PRD 105 Phase 1 — the four standard checks, wired as explicit {@code @Bean}s (AGENTS.md §3: the
 * server does not component-scan its internals). Each one ADAPTS an existing tier-neutral rule
 * function to the server's data sources; none of them re-implements a rule.
 */
@Configuration(proxyBeanMethods = false)
public class StandardCheckers
{
    private static final Logger LOGGER = LoggerFactory.getLogger(StandardCheckers.class);

    /**
     * The three draft-only rules plus {@code NOT_IN_CALENDAR}.
     *
     * <p>D3 — Swing's {@code CalendarModelImpl.isMatchingSelectionAndFilter} intersects the
     * reservation's allocatables ∪ type ∪ owner with the model's selected objects AND applies the
     * model's classification filter. Only the intersection half is reproducible here: the SPA's
     * filter lives inside a stored view query that cannot be run against an unpersisted draft. With
     * no scope stated the check is skipped entirely — an absent scope is not an empty scope.
     */
    @Bean
    @Order(10)
    public ReservationChecker defaultRulesChecker()
    {
        return context ->
        {
            boolean scoped = context.scopeAllocatableIds() != null && !context.scopeAllocatableIds().isEmpty();
            return DefaultReservationWarnings.evaluate(
                    context.asCollection(),
                    context.locale(),
                    false,
                    scoped && context.isEnabled(ReservationWarning.Code.NOT_IN_CALENDAR),
                    r ->
                    {
                        Set<String> scope = context.scopeAllocatableIds();
                        for (Allocatable a : r.getAllocatables())
                        {
                            if (scope.contains(a.getId())) return true;
                        }
                        return r.getOwnerRef() != null && scope.contains(r.getOwnerRef().getId());
                    },
                    appointment -> String.valueOf(appointment.getStart()));
        };
    }

    /**
     * Conflicts for a draft. The operator computes them for a transient reservation
     * ({@code getConflictsSync(Reservation)} — the same call Swing reaches through the facade), so
     * nothing is re-derived client-side and repeating rules expand server-side (D9).
     */
    @Bean
    @Order(20)
    public ReservationChecker conflictChecker(StorageOperator operator)
    {
        return context ->
        {
            if (!context.isEnabled(ReservationWarning.Code.CONFLICT)) return List.of();
            if (!(operator instanceof SyncStorageOperator sync)) return List.of();
            try
            {
                // AGENTS.md §3 — the Sync variant, never Promise+latch on the server.
                if (sync.getConflictsSync(context.reservation()).isEmpty()) return List.of();
                return List.of(ReservationWarning.of(ReservationWarning.Code.CONFLICT));
            }
            catch (Exception e)
            {
                LOGGER.warn("Conflict check failed for a draft of {}", context.caller(), e);
                return List.of();
            }
        };
    }

    /**
     * Holidays hit by the draft's appointments, per occurrence (D9) — {@code findHolidayConflicts}
     * walks each appointment's periods, and {@code filterByPreference} applies the caller's two
     * holiday switches, exactly as Swing's {@code HolidayExceptionCheck} does.
     */
    @Bean
    @Order(30)
    public ReservationChecker holidayChecker(StorageOperator operator)
    {
        return context ->
        {
            if (!context.isEnabled(ReservationWarning.Code.HOLIDAY_ON_APPOINTMENT)) return List.of();
            try
            {
                PeriodModel holidays = operator.getPeriodModelFor("holiday");
                if (holidays == null) holidays = operator.getPeriodModelFor("feiertag");
                Map<Appointment, Set<Period>> hits =
                        HolidayWarningModel.findHolidayConflicts(holidays, context.asCollection());
                Preferences preferences = operator.getPreferences(context.caller(), false);
                boolean single = preferences == null
                        || preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_HOLIDAY_WARNING_SINGLE_APPOINTMENT, true);
                Map<Appointment, Set<Period>> filtered =
                        HolidayWarningModel.filterByPreference(hits, true, single);
                List<ReservationWarning> out = new ArrayList<>();
                for (Appointment appointment : filtered.keySet())
                {
                    out.add(ReservationWarning.of(ReservationWarning.Code.HOLIDAY_ON_APPOINTMENT,
                            String.valueOf(appointment.getStart())));
                }
                return out;
            }
            catch (Exception e)
            {
                LOGGER.warn("Holiday check failed for a draft of {}", context.caller(), e);
                return List.of();
            }
        };
    }

    /** Allocations sitting in {@code REQUESTED} — the pure rule, unchanged (PRD 091 OQ5 supplies the state). */
    @Bean
    @Order(40)
    public ReservationChecker requestPendingChecker()
    {
        return context -> RequestAllocationWarnings.evaluate(context.asCollection(), context.locale());
    }
}
