package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.client.edit.reservation.AllocationConflictModel;
import org.rapla.client.edit.reservation.AllocationConflictModel.AllocationOutcome;
import org.rapla.client.edit.reservation.RepeatingRuleModel;
import org.rapla.client.edit.reservation.RepeatingRuleValidator;
import org.rapla.client.edit.reservation.RepeatingRuleWriter;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.reservationedit.AllocationOutcomeDto;
import org.rapla.plugin.reservationedit.AppointmentBlockDto;
import org.rapla.plugin.reservationedit.AppointmentSpec;
import org.rapla.plugin.reservationedit.ConflictCheckRequest;
import org.rapla.plugin.reservationedit.ConflictReport;
import org.rapla.plugin.reservationedit.ExpandBlocksRequest;
import org.rapla.plugin.reservationedit.RecurrenceRule;
import org.rapla.plugin.reservationedit.RecurrenceValidation;
import org.rapla.plugin.reservationedit.ReservationEditService;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.server.RemoteSession;
import org.rapla.storage.PermissionController;
import org.rapla.storage.SyncStorageOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST surface for edit-time pre-checks (PRD 024 Phase 1).
 * <p>
 * Pure delegate to {@link RepeatingRuleValidator}. No facade hit. Just
 * the JWT gate via {@link RemoteSession#checkAndGetUser}.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class ReservationEditController implements ReservationEditService
{
    private final RemoteSession session;
    private final HttpServletRequest request;
    private final RaplaFacade facade;
    private final SyncStorageOperator syncOperator;

    public ReservationEditController(RemoteSession session, HttpServletRequest request,
                                     RaplaFacade facade, SyncStorageOperator syncOperator)
    {
        this.session = session;
        this.request = request;
        this.facade = facade;
        this.syncOperator = syncOperator;
    }

    @Override
    public RecurrenceValidation validateRecurrence(RecurrenceRule rule) throws RaplaException
    {
        // JWT-gate even though the validator has no permission-side-effect —
        // we don't want anonymous callers probing the endpoint.
        session.checkAndGetUser(request);

        LocalDateTime appointmentStart = rule.appointmentStart() != null
                ? rule.appointmentStart()
                : LocalDateTime.now();

        RepeatingRuleModel model = new RepeatingRuleModel(
                rule.type(),
                rule.interval(),
                rule.weekdays(),
                rule.endingMode(),
                rule.endDate(),
                rule.repeatCount());

        RepeatingRuleValidator.Result result = RepeatingRuleValidator.validate(model, appointmentStart);
        return new RecurrenceValidation(
                result.isValid(),
                result.issues().stream()
                        .map(i -> new RecurrenceValidation.Issue(i.code().name(), i.detail()))
                        .collect(Collectors.toList()));
    }

    @Override
    public ConflictReport checkConflicts(ConflictCheckRequest req) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        PermissionController permissionController = facade.getPermissionController();

        // Resolve + filter allocatables. Per AGENTS.md §12: unknown ids
        // AND ids the user can't read are silently dropped — a non-admin
        // must not be able to probe for the existence of hidden resources.
        List<Allocatable> allocatables = new ArrayList<>();
        for (String id : req.allocatableIds())
        {
            Allocatable a = facade.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
            if (a == null) continue;
            if (!permissionController.canRead(a, user)) continue;
            allocatables.add(a);
        }

        // Build transient Appointment[] from the wire specs.
        Appointment[] candidateAppointments = buildCandidateAppointments(req.appointments());

        // Build ignore list from the candidate appointments' own reservations
        // (same logic as FacadeImpl.getAllocatableBindings).
        Collection<Reservation> ignoreList = new HashSet<>();
        for (Appointment app : candidateAppointments)
        {
            Reservation r = app.getReservation();
            if (r != null) ignoreList.add(r);
        }

        // Sync call — server operator is LocalAbstractCachableOperator which implements
        // SyncStorageOperator; no Promise/latch needed.
        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> nested =
                syncOperator.getAllAllocatableBindingsSync(
                        allocatables, java.util.Arrays.asList(candidateAppointments), ignoreList);

        // Flatten: keep only allocatables that have at least one conflicting appointment.
        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> bindings = new HashMap<>();
        for (Map.Entry<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> e : nested.entrySet())
        {
            for (Map.Entry<Appointment, Collection<Appointment>> ae : e.getValue().entrySet())
            {
                if (!ae.getValue().isEmpty())
                    bindings.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(ae.getKey());
            }
        }

        LocalDate today = req.today() != null ? req.today() : facade.today();

        // Per-allocatable AllocationConflictModel.compute → DTO.
        List<AllocationOutcomeDto> outcomes = new ArrayList<>(allocatables.size());
        for (Allocatable a : allocatables)
        {
            AllocationOutcome out = AllocationConflictModel.compute(
                    a, candidateAppointments, bindings, permissionController, user, today);
            outcomes.add(new AllocationOutcomeDto(
                    a.getId(),
                    out.conflictingAppointments(),
                    out.conflictCount(),
                    out.permissionConflictCount(),
                    out.aggregateRequestStatus() == null ? null : out.aggregateRequestStatus().toString()));
        }
        return new ConflictReport(outcomes);
    }

    @Override
    public List<AppointmentBlockDto> expandBlocks(ExpandBlocksRequest req) throws RaplaException
    {
        session.checkAndGetUser(request);   // JWT gate
        if (req == null || req.appointment() == null)
            throw new IllegalArgumentException("appointment must not be null");
        if (req.windowStart() == null || req.windowEnd() == null)
            throw new IllegalArgumentException("windowStart / windowEnd must not be null");

        // Build a transient AppointmentImpl from the wire spec — same path
        // checkConflicts uses for its candidate appointments.
        Appointment[] arr = buildCandidateAppointments(List.of(req.appointment()));
        Appointment appointment = arr[0];

        List<AppointmentBlock> raw = new ArrayList<>();
        appointment.createBlocks(req.windowStart(), req.windowEnd(), raw, req.excludeExceptions());

        List<AppointmentBlockDto> out = new ArrayList<>(raw.size());
        for (AppointmentBlock b : raw)
        {
            // AppointmentBlock carries millis-since-epoch; convert to LocalDateTime
            // in UTC to match PRD 014's timezone-naive wire convention.
            out.add(new AppointmentBlockDto(
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(b.getStart()), java.time.ZoneOffset.UTC),
                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(b.getEnd()),   java.time.ZoneOffset.UTC)));
        }
        return out;
    }

    private static Appointment[] buildCandidateAppointments(List<AppointmentSpec> specs)
    {
        Appointment[] out = new Appointment[specs.size()];
        for (int i = 0; i < specs.size(); i++)
        {
            AppointmentSpec s = specs.get(i);
            AppointmentImpl a = new AppointmentImpl(s.start(), s.end());
            if (s.recurrence() != null)
            {
                a.setRepeatingEnabled(true);
                RecurrenceRule rule = s.recurrence();
                RepeatingRuleModel model = new RepeatingRuleModel(
                        rule.type(), rule.interval(), rule.weekdays(),
                        rule.endingMode(), rule.endDate(), rule.repeatCount());
                RepeatingRuleWriter.writeTo(model, a.getRepeating(), s.start());
            }
            out[i] = a;
        }
        return out;
    }

}
