package org.rapla.server.spring.graphql;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.rapla.client.edit.reservation.RepeatingRuleModel;
import org.rapla.client.edit.reservation.RepeatingRuleProjector;
import org.rapla.client.edit.reservation.RepeatingRuleWriter;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.UpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

/**
 * PRD 056 — events mutation controller. Implements the 6+1 mutation surface:
 * single-entity full-state create/update, four bulk transforms (changeOwner,
 * move, copy, deleteMany), and the generic applyChanges escape hatch.
 *
 * <p>All writes flow through {@code dispatchChecked(UpdateEvent)} — one
 * UpdateEvent per call (ATOMIC-only in v1; PARTIAL deferred to PRD 058).
 *
 * <p>§12 gates are inline per-verb. Anonymous → reject; non-admin →
 * permission-walk check on every referenced entity.
 *
 * <p>v1 implementation focuses on the canonical happy path for
 * createReservation / updateReservation / deleteReservations / applyChanges.
 * The bulk-transform verbs (changeOwner, move, copy) ship as thin wrappers
 * that build UpdateEvent batches; full validation (conflict detection,
 * permission-graph walk) lands as the consumer audience grows.
 *
 * <p>Input shape: the classification field arrives as a Map (Jackson +
 * Spring deserialization of the {@code @oneOf} ClassificationInput).
 * Exactly one key in the map corresponds to a DynamicType variant; the
 * value is a Map of attribute key → typed value. Server validates the
 * variant matches the outer typeId.
 */
@Controller
public class ReservationMutationController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationMutationController.class);

    private final CachableStorageOperator operator;
    private final org.rapla.server.spring.JwtUserResolver jwtUserResolver;

    private final org.rapla.server.internal.SecurityManager security;

    public ReservationMutationController(StorageOperator operator,
            org.rapla.server.spring.JwtUserResolver jwtUserResolver,
            org.rapla.server.internal.SecurityManager security)
    {
        this.security = security;
        if (!(operator instanceof CachableStorageOperator c))
        {
            throw new IllegalStateException(
                    "ReservationMutationController requires a CachableStorageOperator; got "
                            + (operator == null ? "null" : operator.getClass().getName()));
        }
        this.operator = c;
        this.jwtUserResolver = jwtUserResolver;
    }

    /** Same write gate as the Swing wire ({@code RemoteStorageController.dispatch_}): the
     *  {@code requireCan*} pre-checks are cheap hints only — {@code SecurityManager} owns
     *  canAllocate, conflict rights and the allocation-change-needs-admin rule. */
    private void dispatchChecked(UpdateEvent event) throws RaplaException
    {
        WriteGate.check(security, operator, event);
        operator.dispatch(event);
    }

    private Reservation editObject(Reservation source)
    {
        Reservation draft = (Reservation) source.clone();
        return draft;
    }

    // ============================================================ editor flow

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Reservation createReservation(@Argument("input") Map<String, Object> input) throws RaplaException
    {
        User caller = requireCaller();
        String typeKey = (String) input.get("typeKey");
        if (typeKey == null || typeKey.isBlank())
        {
            throw new ReservationMutationException("REQUIRED",
                    "operations[0].createReservation.typeKey", "typeKey is required");
        }
        DynamicType dt = resolveType(typeKey);
        requireCanCreate(dt, caller);

        // Build classification + reservation
        Map<String, Object> classificationInput = (Map<String, Object>) input.get("classification");
        Classification classification = buildClassificationFromInput(dt, classificationInput, typeKey);

        ReservationImpl r = new ReservationImpl(operator.getCurrentTimestamp(), operator.getCurrentTimestamp());
        r.setClassification(classification);
        r.setOwner(caller);
        // PRD 099 Phase 1 — Swing parity (FacadeImpl.newReservation): seed the
        // new reservation with the type's permissions (all but CREATE/READ_TYPE).
        org.rapla.entities.domain.PermissionContainer.Util.copyPermissions(dt, r);
        // PRD 056 §9 (2026-07-06): client id is REQUIRED — no server fallback.
        // Retry-idempotency works via ID_COLLISION on the client-minted id
        // (CalDAV model); a server-generated id can never be retry-safe.
        String clientId = (String) input.get("id");
        if (clientId == null || clientId.isBlank())
        {
            throw new ReservationMutationException("REQUIRED",
                    "operations[0].createReservation.id",
                    "id is required — clients mint their own entity ids (PRD 056 §9)");
        }
        r.setId(clientId);

        // Appointments (required ≥1 — PRD 056 OQ1.d)
        List<Map<String, Object>> appointments = (List<Map<String, Object>>) input.get("appointments");
        if (appointments == null || appointments.isEmpty())
        {
            throw new ReservationMutationException("REQUIRED",
                    "operations[0].createReservation.appointments",
                    "appointments must have at least one entry");
        }
        Map<String, Appointment> appointmentByClientId = new LinkedHashMap<>();
        for (int i = 0; i < appointments.size(); i++)
        {
            Map<String, Object> ai = appointments.get(i);
            Appointment a = buildAppointment(ai, "operations[0].createReservation.appointments[" + i + "]");
            r.addAppointment(a);
            String aClientId = (String) ai.get("id");
            if (aClientId != null) appointmentByClientId.put(aClientId, a);
            appointmentByClientId.put(a.getId(), a);    // also resolves real-id refs
        }

        // Allocations
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) input.get("allocations");
        if (allocations != null)
        {
            applyAllocations(r, allocations, appointmentByClientId, caller,
                    "operations[0].createReservation.allocations");
        }

        // Dispatch — declared as CREATE so checkIdIntegrity #1 rejects an
        // already-existing id with ID_COLLISION (retry contract, PRD 056 §9)
        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        event.addStore(r);
        event.addCreate(r.getReference());
        dispatchChecked(event);

        // Re-resolve to get the stored reservation (with all derived fields populated)
        return (Reservation) operator.tryResolve(r.getReference());
    }

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Reservation updateReservation(@Argument("id") String id,
                                          @Argument("input") Map<String, Object> input,
                                          @Argument("expectedLastChanged") LocalDateTime expectedLastChanged)
            throws RaplaException
    {
        User caller = requireCaller();
        if (id == null || id.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", "id", "id is required");
        }
        Reservation stored = operator.tryResolve(new ReferenceInfo<>(id, Reservation.class));
        if (stored == null)
        {
            throw new ReservationMutationException("REFERENCE_NOT_FOUND", "id",
                    "Reservation " + id + " not found");
        }
        requireCanModify(stored, caller);

        // OQ1.c revised 2026-07-07 (PRD 096) — type change accepted; the
        // @oneOf variant must match the NEW typeKey, creation gate on the target type
        String inputTypeKey = (String) input.get("typeKey");
        String storedTypeKey = stored.getClassification().getType().getKey();
        DynamicType targetType = stored.getClassification().getType();
        String targetTypeKey = storedTypeKey;
        if (inputTypeKey != null && !inputTypeKey.equals(storedTypeKey))
        {
            targetType = resolveType(inputTypeKey);
            requireCanCreate(targetType, caller);
            targetTypeKey = inputTypeKey;
        }

        // Optimistic concurrency
        if (expectedLastChanged != null && stored.getLastChanged() != null
                && !expectedLastChanged.equals(stored.getLastChanged()))
        {
            throw new ReservationMutationException("CONCURRENT_MODIFICATION",
                    "expectedLastChanged",
                    "Reservation was modified after the supplied lastChanged timestamp");
        }

        // Clone for edit (rapla pattern — never mutate persistent entities)
        ReservationImpl draft = (ReservationImpl) editObject(stored);

        // Replace classification attrs from input
        Map<String, Object> classificationInput = (Map<String, Object>) input.get("classification");
        Classification newClassification = buildClassificationFromInput(targetType, classificationInput, targetTypeKey);
        draft.setClassification(newClassification);

        // Replace appointments
        for (Appointment existing : draft.getAppointments())
        {
            draft.removeAppointment(existing);
        }
        List<Map<String, Object>> appointments = (List<Map<String, Object>>) input.get("appointments");
        if (appointments == null || appointments.isEmpty())
        {
            throw new ReservationMutationException("REQUIRED",
                    "input.appointments", "appointments must have at least one entry");
        }
        Map<String, Appointment> appointmentByClientId = new LinkedHashMap<>();
        for (int i = 0; i < appointments.size(); i++)
        {
            Map<String, Object> ai = appointments.get(i);
            Appointment a = buildAppointment(ai, "input.appointments[" + i + "]");
            draft.addAppointment(a);
            String aClientId = (String) ai.get("id");
            if (aClientId != null) appointmentByClientId.put(aClientId, a);
            appointmentByClientId.put(a.getId(), a);
        }

        // Replace allocations
        for (Allocatable existing : draft.getAllocatables())
        {
            draft.removeAllocatable(existing);
        }
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) input.get("allocations");
        if (allocations != null)
        {
            applyAllocations(draft, allocations, appointmentByClientId, caller, "input.allocations");
        }

        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        event.addStore(draft);
        dispatchChecked(event);

        return (Reservation) operator.tryResolve(draft.getReference());
    }

    // ============================================================ bulk transforms

    @MutationMapping
    public Map<String, Object> changeReservationOwner(@Argument("ids") List<String> ids,
                                                       @Argument("newOwnerId") String newOwnerId)
            throws RaplaException
    {
        User caller = requireCaller();
        User newOwner = operator.tryResolve(new ReferenceInfo<>(newOwnerId, User.class));
        if (newOwner == null)
        {
            throw new ReservationMutationException("REFERENCE_NOT_FOUND", "newOwnerId",
                    "User " + newOwnerId + " not found");
        }
        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        List<Map<String, Object>> results = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++)
        {
            String id = ids.get(i);
            Reservation r = operator.tryResolve(new ReferenceInfo<>(id, Reservation.class));
            if (r == null)
            {
                throw new ReservationMutationException("REFERENCE_NOT_FOUND", "ids[" + i + "]",
                        "Reservation " + id + " not found");
            }
            requireCanModify(r, caller);
            Reservation draft = editObject(r);
            draft.setOwner(newOwner);
            event.addStore(draft);
            results.add(bulkEntry(i, draft, null, null, null));
        }
        dispatchChecked(event);
        return bulkResult("SUCCESS", results);
    }

    @MutationMapping
    public Map<String, Object> moveReservations(@Argument("ids") List<String> ids,
                                                 @Argument("reference") LocalDateTime reference,
                                                 @Argument("target") Map<String, Object> target)
            throws RaplaException
    {
        User caller = requireCaller();
        List<Reservation> resolved = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++)
        {
            Reservation r = operator.tryResolve(new ReferenceInfo<>(ids.get(i), Reservation.class));
            if (r == null) throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                    "ids[" + i + "]", "Reservation " + ids.get(i) + " not found");
            requireCanModify(r, caller);
            resolved.add(r);
        }
        // reference = arithmetic pivot; default = earliest appointment start across the set (PRD 101).
        LocalDateTime pivot = reference;
        if (pivot == null)
        {
            for (Reservation r : resolved)
                for (Appointment a : r.getAppointments())
                    if (pivot == null || a.getStart().isBefore(pivot)) pivot = a.getStart();
        }
        if (pivot == null) throw new ReservationMutationException("REQUIRED", "reference",
                "no appointments to derive a reference from");
        Duration delta = resolveTargetDeltas(target, pivot, null)[0]; // move-only → endDelta = startDelta

        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        List<Map<String, Object>> results = new ArrayList<>(ids.size());
        for (int i = 0; i < resolved.size(); i++)
        {
            Reservation draft = editObject(resolved.get(i));
            for (Appointment a : draft.getAppointments())
            {
                a.move(a.getStart().plus(delta), a.getEnd().plus(delta));
            }
            event.addStore(draft);
            results.add(bulkEntry(i, draft, null, null, null));
        }
        dispatchChecked(event);
        return bulkResult("SUCCESS", results);
    }

    @MutationMapping
    public Reservation moveAppointment(@Argument("appointmentId") String appointmentId,
                                       @Argument("occurrence") LocalDateTime occurrence,
                                       @Argument("target") Map<String, Object> target,
                                       @Argument("expectedLastChanged") LocalDateTime expectedLastChanged)
            throws RaplaException
    {
        User caller = requireCaller();
        Appointment stored = resolveAppointment(appointmentId);
        Reservation r = stored.getReservation();
        requireCanModify(r, caller);
        checkConcurrency(r, expectedLastChanged);
        LocalDateTime occ = occurrence != null ? occurrence : stored.getStart();
        if (occurrence != null) requireOccurrence(stored, occurrence);
        LocalDateTime occEnd = occ.plus(Duration.between(stored.getStart(), stored.getEnd()));
        Duration[] d = resolveTargetDeltas(target, occ, occEnd);

        Reservation draft = editObject(r);
        Appointment appt = findAppointment(draft, appointmentId);
        appt.move(appt.getStart().plus(d[0]), appt.getEnd().plus(d[1]));
        dispatchStore(caller, draft);
        return draft;
    }

    /**
     * PRD 105 — the state {@link #moveAppointment} WOULD store, without storing it: same resolution,
     * same permission gate, same shift arithmetic, no dispatch. The pre-save checks run against this,
     * so the SPA never rebuilds a move payload itself (PRD 101 keeps move semantics server-side).
     */
    Reservation prospectiveAppointmentMove(String appointmentId, LocalDateTime occurrence,
            Map<String, Object> target, User caller) throws RaplaException
    {
        Appointment stored = resolveAppointment(appointmentId);
        Reservation r = stored.getReservation();
        requireCanModify(r, caller);
        LocalDateTime occ = occurrence != null ? occurrence : stored.getStart();
        if (occurrence != null) requireOccurrence(stored, occurrence);
        LocalDateTime occEnd = occ.plus(Duration.between(stored.getStart(), stored.getEnd()));
        Duration[] d = resolveTargetDeltas(target, occ, occEnd);
        Reservation draft = editObject(r);
        Appointment appt = findAppointment(draft, appointmentId);
        appt.move(appt.getStart().plus(d[0]), appt.getEnd().plus(d[1]));
        ((ReservationImpl) draft).setResolver(operator);
        return draft;
    }

    /** PRD 105 — the same for a whole-reservation shift ({@link #moveReservations}); no dispatch. */
    List<Reservation> prospectiveReservationsMove(List<String> ids, LocalDateTime reference,
            Map<String, Object> target, User caller) throws RaplaException
    {
        List<Reservation> drafts = new ArrayList<>(ids.size());
        LocalDateTime pivot = reference;
        List<Reservation> resolved = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++)
        {
            Reservation r = operator.tryResolve(new ReferenceInfo<>(ids.get(i), Reservation.class));
            if (r == null) throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                    "ids[" + i + "]", "Reservation " + ids.get(i) + " not found");
            requireCanModify(r, caller);
            resolved.add(r);
            if (pivot == null)
            {
                for (Appointment a : r.getAppointments())
                    if (pivot == null || a.getStart().isBefore(pivot)) pivot = a.getStart();
            }
        }
        if (pivot == null) throw new ReservationMutationException("REQUIRED", "reference",
                "no appointments to derive a reference from");
        for (Reservation r : resolved)
        {
            Reservation draft = editObject(r);
            for (Appointment a : draft.getAppointments())
            {
                LocalDateTime end = pivot.plus(Duration.between(a.getStart(), a.getEnd()));
                Duration[] d = resolveTargetDeltas(target, pivot, end);
                a.move(a.getStart().plus(d[0]), a.getEnd().plus(d[1]));
            }
            ((ReservationImpl) draft).setResolver(operator);
            drafts.add(draft);
        }
        return drafts;
    }

    @MutationMapping
    public Reservation splitOccurrence(@Argument("appointmentId") String appointmentId,
                                       @Argument("occurrence") LocalDateTime occurrence,
                                       @Argument("target") Map<String, Object> target,
                                       @Argument("expectedLastChanged") LocalDateTime expectedLastChanged)
            throws RaplaException
    {
        User caller = requireCaller();
        Appointment stored = resolveAppointment(appointmentId);
        Reservation r = stored.getReservation();
        requireCanModify(r, caller);
        checkConcurrency(r, expectedLastChanged);
        if (stored.getRepeating() == null)
        {
            throw new ReservationMutationException("INVALID_VALUE", "appointmentId",
                    "splitOccurrence requires a repeating appointment; use moveAppointment");
        }
        requireOccurrence(stored, occurrence);
        LocalDateTime occEnd = occurrence.plus(Duration.between(stored.getStart(), stored.getEnd()));
        Duration[] d = resolveTargetDeltas(target, occurrence, occEnd);

        Reservation draft = editObject(r);
        Appointment original = findAppointment(draft, appointmentId);

        // capture the allocatables restricted to the original appointment (id-list restrictions)
        List<Allocatable> restrictedTo = new ArrayList<>();
        for (Allocatable al : draft.getAllocatables())
        {
            Appointment[] restriction = draft.getRestriction(al);
            for (Appointment ra : restriction)
                if (ra.equals(original)) { restrictedTo.add(al); break; }
        }

        // add the exception on the original series; if it empties, remove the whole appointment
        original.getRepeating().addException(DateTools.cutDate(occurrence));
        if (!isNotEmpty(original))
        {
            draft.removeAppointment(original);
            if (draft.getAppointments().length == 0)
            {
                // whole reservation emptied — delete it
                UpdateEvent del = new UpdateEvent();
                del.setUserId(caller.getId());
                del.putRemoveId(new ReferenceInfo<>(r.getId(), Reservation.class));
                dispatchChecked(del);
                return r; // pre-delete snapshot
            }
        }

        // clone the grabbed occurrence as a new non-repeating appointment at the target
        Appointment clone = (Appointment) original.clone();
        clone.setRepeatingEnabled(false);
        ReferenceInfo<Appointment> cloneId = operator.createIdentifier(Appointment.class, 1).get(0);
        ((AppointmentImpl) clone).setId(cloneId.getId());
        clone.move(occurrence.plus(d[0]), occEnd.plus(d[1]));
        draft.addAppointment(clone);
        // carry the original appointment's restrictions onto the clone
        for (Allocatable al : restrictedTo)
        {
            Appointment[] existing = draft.getRestriction(al);
            Appointment[] extended = new Appointment[existing.length + 1];
            System.arraycopy(existing, 0, extended, 0, existing.length);
            extended[existing.length] = clone;
            draft.setRestriction(al, extended);
        }
        dispatchStore(caller, draft);
        return draft;
    }

    @MutationMapping
    public Map<String, Object> copyReservations(@Argument("ids") List<String> ids,
                                                 @Argument("reference") LocalDateTime reference,
                                                 @Argument("target") Map<String, Object> target)
            throws RaplaException
    {
        User caller = requireCaller();
        List<Reservation> sources = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++)
        {
            Reservation src = operator.tryResolve(new ReferenceInfo<>(ids.get(i), Reservation.class));
            if (src == null) throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                    "ids[" + i + "]", "Reservation " + ids.get(i) + " not found");
            requireCanRead(src, caller);
            requireCanCreate(src.getClassification().getType(), caller);
            sources.add(src);
        }
        LocalDateTime pivot = reference;
        if (pivot == null)
        {
            for (Reservation r : sources)
                for (Appointment a : r.getAppointments())
                    if (pivot == null || a.getStart().isBefore(pivot)) pivot = a.getStart();
        }
        if (pivot == null) throw new ReservationMutationException("REQUIRED", "reference",
                "no appointments to derive a reference from");
        Duration delta = resolveTargetDeltas(target, pivot, null)[0];

        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        List<Map<String, Object>> results = new ArrayList<>(ids.size());
        for (int i = 0; i < sources.size(); i++)
        {
            Reservation copy = (Reservation) sources.get(i).clone();
            ReferenceInfo<Reservation> newId = operator.createIdentifier(Reservation.class, 1).get(0);
            ((ReservationImpl) copy).setId(newId.getId());
            copy.setOwner(caller);
            // clone() keeps appointment ids — a copy must mint fresh ones (checkIdIntegrity #2).
            // Restrictions are appointment-id lists → rewrite against the new ids.
            Map<Allocatable, Appointment[]> restrictions = new LinkedHashMap<>();
            for (Allocatable al : copy.getAllocatables())
            {
                Appointment[] restriction = copy.getRestriction(al);
                if (restriction != null && restriction.length > 0) restrictions.put(al, restriction);
            }
            Appointment[] copiedAppointments = copy.getAppointments();
            List<ReferenceInfo<Appointment>> newApptIds =
                    operator.createIdentifier(Appointment.class, copiedAppointments.length);
            for (int j = 0; j < copiedAppointments.length; j++)
                ((AppointmentImpl) copiedAppointments[j]).setId(newApptIds.get(j).getId());
            for (Map.Entry<Allocatable, Appointment[]> entry : restrictions.entrySet())
                copy.setRestriction(entry.getKey(), entry.getValue());
            for (Appointment a : copy.getAppointments())
            {
                LocalDateTime oldStart = a.getStart();
                a.move(oldStart.plus(delta), a.getEnd().plus(delta));
                // D2: exceptions stay absolute (do NOT re-base). D3: non-fixed `until`
                // is length-preserving on copy, else a far-range copy is empty.
                Repeating rep = a.getRepeating();
                if (rep != null && !rep.isFixedNumber() && rep.getEnd() != null)
                {
                    long lenDays = DateTools.countDays(oldStart, rep.getEnd());
                    rep.setEnd(DateTools.addDays(a.getStart(), lenDays));
                }
            }
            event.addStore(copy);
            event.addCreate(copy.getReference());
            results.add(bulkEntry(i, copy, null, null, null));
        }
        dispatchChecked(event);
        return bulkResult("SUCCESS", results);
    }

    // ============================================ PRD 101 transpose helpers

    /** Resolve an appointment id to its (read-only) stored appointment. */
    private Appointment resolveAppointment(String appointmentId)
    {
        Appointment a = operator.tryResolve(new ReferenceInfo<>(appointmentId, Appointment.class));
        if (a == null) throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                "appointmentId", "Appointment " + appointmentId + " not found");
        return a;
    }

    private Appointment findAppointment(Reservation draft, String appointmentId)
    {
        for (Appointment a : draft.getAppointments())
            if (appointmentId.equals(a.getId())) return a;
        throw new ReservationMutationException("REFERENCE_NOT_FOUND", "appointmentId",
                "Appointment " + appointmentId + " not found in reservation");
    }

    /**
     * Resolve (startDelta, endDelta) from a target (@oneOf day|dateTime) against a reference
     * start/end (PRD 101). day → whole-day delta keeping time-of-day; dateTime scalar (Target)
     * → exact start shift; DateTimeTarget (ResizableTarget) → exact start shift + optional end
     * (resize). endDelta defaults to startDelta (pure move).
     */
    @SuppressWarnings("unchecked")
    private Duration[] resolveTargetDeltas(Map<String, Object> target, LocalDateTime referenceStart,
                                           LocalDateTime referenceEnd)
    {
        Object day = target.get("day");
        Object dateTime = target.get("dateTime");
        LocalDateTime newStart;
        LocalDateTime newEnd = null;
        if (day instanceof LocalDate d)
        {
            newStart = LocalDateTime.of(d, referenceStart.toLocalTime());
        }
        else if (dateTime instanceof LocalDateTime ldt)
        {
            newStart = ldt;
        }
        else if (dateTime instanceof Map)
        {
            Map<String, Object> dt = (Map<String, Object>) dateTime;
            newStart = (LocalDateTime) dt.get("start");
            newEnd = (LocalDateTime) dt.get("end");
        }
        else
        {
            throw new ReservationMutationException("REQUIRED", "target",
                    "target must set exactly one of day / dateTime");
        }
        Duration startDelta = Duration.between(referenceStart, newStart);
        Duration endDelta = (newEnd != null && referenceEnd != null)
                ? Duration.between(referenceEnd, newEnd) : startDelta;
        return new Duration[] { startDelta, endDelta };
    }

    /** Validate that {@code occurrence} is a real block start of the appointment. */
    private void requireOccurrence(Appointment appointment, LocalDateTime occurrence)
    {
        List<AppointmentBlock> blocks = new ArrayList<>();
        appointment.createBlocks(occurrence, occurrence.plusNanos(1), blocks);
        for (AppointmentBlock b : blocks)
            if (b.getStartDateTime().equals(occurrence)) return;
        throw new ReservationMutationException("OCCURRENCE_NOT_FOUND", "occurrence",
                "No occurrence starts at " + occurrence);
    }

    private void checkConcurrency(Reservation r, LocalDateTime expectedLastChanged)
    {
        if (expectedLastChanged != null && r.getLastChanged() != null
                && !expectedLastChanged.equals(r.getLastChanged()))
        {
            throw new ReservationMutationException("CONCURRENT_MODIFICATION", "expectedLastChanged",
                    "Reservation was modified concurrently");
        }
    }

    private void dispatchStore(User caller, Reservation draft) throws RaplaException
    {
        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        event.addStore(draft);
        dispatchChecked(event);
    }

    /** True if the appointment still produces ≥1 block (mirrors isNotEmptyWithExceptions). */
    private boolean isNotEmpty(Appointment appointment)
    {
        List<AppointmentBlock> blocks = new ArrayList<>();
        appointment.createBlocks(appointment.getStart(), DateTools.addYears(appointment.getStart(), 4), blocks);
        return !blocks.isEmpty();
    }

    @MutationMapping
    public Map<String, Object> deleteReservations(@Argument("ids") List<String> ids) throws RaplaException
    {
        User caller = requireCaller();
        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        List<Map<String, Object>> results = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++)
        {
            String id = ids.get(i);
            Reservation r = operator.tryResolve(new ReferenceInfo<>(id, Reservation.class));
            if (r == null) throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                    "ids[" + i + "]", "Reservation " + id + " not found");
            requireCanModify(r, caller);
            event.putRemoveId(new ReferenceInfo<>(id, Reservation.class));
            results.add(bulkEntry(i, null, "RESERVATION", id, null));
        }
        dispatchChecked(event);
        return bulkResult("SUCCESS", results);
    }

    // ============================================================ applyChanges

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyChanges(@Argument("operations") List<Map<String, Object>> operations)
            throws RaplaException
    {
        User caller = requireCaller();
        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        List<Map<String, Object>> results = new ArrayList<>(operations.size());
        Map<String, Reservation> sameBatchCreated = new LinkedHashMap<>();

        for (int i = 0; i < operations.size(); i++)
        {
            Map<String, Object> op = operations.get(i);
            // @oneOf is enforced by the engine — exactly one variant set.
            if (op.containsKey("createReservation"))
            {
                Map<String, Object> ci = (Map<String, Object>) op.get("createReservation");
                Reservation r = buildReservationFromCreateInput(ci, caller,
                        "operations[" + i + "].createReservation");
                event.addStore(r);
                event.addCreate(r.getReference());
                sameBatchCreated.put(r.getId(), r);
                results.add(bulkEntry(i, r, null, null, null));
            }
            else if (op.containsKey("updateReservation"))
            {
                Map<String, Object> ui = (Map<String, Object>) op.get("updateReservation");
                String rId = (String) ui.get("id");
                Map<String, Object> innerInput = (Map<String, Object>) ui.get("input");
                LocalDateTime expectedLc = (LocalDateTime) ui.get("expectedLastChanged");
                Reservation updated = applyUpdateInBatch(rId, innerInput, expectedLc, caller,
                        "operations[" + i + "].updateReservation");
                event.addStore(updated);
                results.add(bulkEntry(i, updated, null, null, null));
            }
            else if (op.containsKey("deleteReservation"))
            {
                Map<String, Object> di = (Map<String, Object>) op.get("deleteReservation");
                String rId = (String) di.get("id");
                LocalDateTime expectedLc = (LocalDateTime) di.get("expectedLastChanged");
                Reservation r = operator.tryResolve(new ReferenceInfo<>(rId, Reservation.class));
                if (r == null) throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                        "operations[" + i + "].deleteReservation.id",
                        "Reservation " + rId + " not found");
                requireCanModify(r, caller);
                if (expectedLc != null && r.getLastChanged() != null && !expectedLc.equals(r.getLastChanged()))
                {
                    throw new ReservationMutationException("CONCURRENT_MODIFICATION",
                            "operations[" + i + "].deleteReservation.expectedLastChanged",
                            "Reservation was modified after the supplied lastChanged timestamp");
                }
                event.putRemoveId(new ReferenceInfo<>(rId, Reservation.class));
                results.add(bulkEntry(i, null, "RESERVATION", rId, null));
            }
            else
            {
                throw new ReservationMutationException("OP_INVARIANT",
                        "operations[" + i + "]",
                        "ChangeOp must have exactly one variant set");
            }
        }

        dispatchChecked(event);
        return bulkResult("SUCCESS", results);
    }

    // ============================================================ helpers

    private DynamicType resolveType(String typeKey) throws RaplaException
    {
        DynamicType dt = ClassificationInputMapper.tryResolveType(operator, typeKey);
        if (dt == null)
        {
            throw new ReservationMutationException("REFERENCE_NOT_FOUND", "typeKey",
                    "DynamicType " + typeKey + " not found");
        }
        return dt;
    }

    @SuppressWarnings("unchecked")
    /** Delegates to the shared {@link ClassificationInputMapper} (dedup 2026-07-08). */
    private Classification buildClassificationFromInput(DynamicType dt, Map<String, Object> classificationInput,
            String expectedTypeKey)
    {
        return ClassificationInputMapper.buildClassificationFromInput(operator, dt, classificationInput, expectedTypeKey);
    }

    private Appointment buildAppointment(Map<String, Object> ai, String path)
    {
        LocalDateTime start = (LocalDateTime) ai.get("start");
        LocalDateTime end = (LocalDateTime) ai.get("end");
        if (start == null || end == null)
        {
            throw new ReservationMutationException("REQUIRED", path + ".start/end",
                    "appointment start and end are required");
        }
        AppointmentImpl a = new AppointmentImpl(start, end);
        // PRD 056 §9 (2026-07-06): appointment id is REQUIRED — allocation
        // restrictions reference appointments by these ids, and only a
        // client-minted id is retry-idempotent. Kills the old B′ conditional
        // ("ids only required when restrictions are present").
        String clientId = (String) ai.get("id");
        if (clientId == null || clientId.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", path + ".id",
                    "appointment id is required — clients mint their own entity ids (PRD 056 §9)");
        }
        a.setId(clientId);
        if (Boolean.TRUE.equals(ai.get("allDay")))
        {
            a.setWholeDays(true);
        }
        // repeating materialization shared with the availability queries
        // (PRD 091 Phase 4.5) — see AppointmentInputMapper
        AppointmentInputMapper.applyRepeating(a, ai, path);
        return a;
    }

    @SuppressWarnings("unchecked")
    private void applyAllocations(Reservation r, List<Map<String, Object>> allocations,
            Map<String, Appointment> appointmentByClientId, User caller, String pathBase)
    {
        for (int i = 0; i < allocations.size(); i++)
        {
            Map<String, Object> alloc = allocations.get(i);
            String allocId = (String) alloc.get("allocatableId");
            Allocatable a = operator.tryResolve(new ReferenceInfo<>(allocId, Allocatable.class));
            if (a == null)
            {
                throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                        pathBase + "[" + i + "].allocatableId",
                        "Allocatable " + allocId + " not found");
            }
            if (caller != null && !operator.getPermissionController().canRead(a, caller))
            {
                // §12 / security-audit A0d — an unreadable id must answer EXACTLY like an unknown
                // one. A distinct PERMISSION_DENIED here was an existence oracle: probing guessed
                // allocatable ids told the caller which of them exist behind their read scope.
                // Same masking MutationExistenceLeakTest already pins for entity ids.
                throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                        pathBase + "[" + i + "].allocatableId",
                        "Allocatable " + allocId + " not found");
            }
            r.addAllocatable(a);
            // PRD 091 OQ5 — mark an allocation the caller may only REQUEST, exactly as Swing does
            // on assignment (RaplaComponent.addAllocatables). Derived here rather than taken from
            // the input: whether a resource is request-only is a permission verdict, and a client
            // could only echo it. Without the marker SecurityManager rejects the whole save
            // (warning.no_reserve_permission), which is what made request-only users stuck.
            if (caller != null
                    && operator.getPermissionController().isRequestOnly(a, caller, operator.today()))
            {
                r.setRequestStatus(a, org.rapla.entities.domain.RequestStatus.REQUESTED);
            }
            List<String> apptIds = (List<String>) alloc.get("appointmentIds");
            if (apptIds != null && !apptIds.isEmpty())
            {
                List<Appointment> restrictTo = new ArrayList<>(apptIds.size());
                for (String apptId : apptIds)
                {
                    Appointment ap = appointmentByClientId.get(apptId);
                    if (ap == null)
                    {
                        throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                                pathBase + "[" + i + "].appointmentIds",
                                "Appointment " + apptId + " not in same-document references");
                    }
                    restrictTo.add(ap);
                }
                r.setRestriction(a, restrictTo.toArray(new Appointment[0]));
            }
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * PRD 105 — the transient reservation a pre-save CHECK runs against, built by the same mapper a
     * save uses (a check that sees a differently-built object is not a check of that save).
     * Package-private: the checks controller lives next door; nothing here is persisted.
     */
    Reservation buildTransientForCheck(Map<String, Object> input, User caller) throws RaplaException
    {
        Reservation r = buildReservationFromCreateInput(input, caller, "reservationChecks.draft");
        // A draft built here has never been through dispatch, so its references are unresolved and
        // getAllocatables() throws "Resolver not set". The write path gets the resolver during
        // dispatch; a check runs BEFORE that and must attach it itself (StorageOperator IS the
        // EntityResolver — same pattern as NotificationService/DefaultUserProvisioner).
        ((ReservationImpl) r).setResolver(operator);
        return r;
    }

    private Reservation buildReservationFromCreateInput(Map<String, Object> input, User caller, String path)
            throws RaplaException
    {
        // Mirror of createReservation logic — used inside applyChanges so we
        // don't have nested @MutationMapping calls
        String typeKey = (String) input.get("typeKey");
        DynamicType dt = resolveType(typeKey);
        requireCanCreate(dt, caller);
        Map<String, Object> classificationInput = (Map<String, Object>) input.get("classification");
        Classification classification = buildClassificationFromInput(dt, classificationInput, typeKey);
        ReservationImpl r = new ReservationImpl(operator.getCurrentTimestamp(), operator.getCurrentTimestamp());
        r.setClassification(classification);
        r.setOwner(caller);
        // PRD 099 Phase 1 — permission create-seed, mirrors createReservation.
        org.rapla.entities.domain.PermissionContainer.Util.copyPermissions(dt, r);
        // PRD 056 §9 (2026-07-06): client id REQUIRED — mirrors createReservation.
        String clientId = (String) input.get("id");
        if (clientId == null || clientId.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", path + ".id",
                    "id is required — clients mint their own entity ids (PRD 056 §9)");
        }
        r.setId(clientId);
        List<Map<String, Object>> appointments = (List<Map<String, Object>>) input.get("appointments");
        if (appointments == null || appointments.isEmpty())
        {
            throw new ReservationMutationException("REQUIRED", path + ".appointments",
                    "appointments must have at least one entry");
        }
        Map<String, Appointment> appointmentByClientId = new LinkedHashMap<>();
        for (int i = 0; i < appointments.size(); i++)
        {
            Map<String, Object> ai = appointments.get(i);
            Appointment a = buildAppointment(ai, path + ".appointments[" + i + "]");
            r.addAppointment(a);
            String aClientId = (String) ai.get("id");
            if (aClientId != null) appointmentByClientId.put(aClientId, a);
            appointmentByClientId.put(a.getId(), a);
        }
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) input.get("allocations");
        if (allocations != null)
        {
            applyAllocations(r, allocations, appointmentByClientId, caller, path + ".allocations");
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private Reservation applyUpdateInBatch(String id, Map<String, Object> input, LocalDateTime expectedLc,
            User caller, String path) throws RaplaException
    {
        Reservation stored = operator.tryResolve(new ReferenceInfo<>(id, Reservation.class));
        if (stored == null) throw new ReservationMutationException("REFERENCE_NOT_FOUND", path + ".id",
                "Reservation " + id + " not found");
        requireCanModify(stored, caller);
        String inputTypeKey = (String) input.get("typeKey");
        String storedTypeKey = stored.getClassification().getType().getKey();
        DynamicType targetType = stored.getClassification().getType();
        String targetTypeKey = storedTypeKey;
        if (inputTypeKey != null && !inputTypeKey.equals(storedTypeKey))
        {
            targetType = resolveType(inputTypeKey);
            requireCanCreate(targetType, caller);
            targetTypeKey = inputTypeKey;
        }
        if (expectedLc != null && stored.getLastChanged() != null && !expectedLc.equals(stored.getLastChanged()))
        {
            throw new ReservationMutationException("CONCURRENT_MODIFICATION", path + ".expectedLastChanged",
                    "Reservation modified after supplied lastChanged");
        }
        Reservation draft = editObject(stored);
        Map<String, Object> classificationInput = (Map<String, Object>) input.get("classification");
        draft.setClassification(buildClassificationFromInput(targetType, classificationInput, targetTypeKey));
        for (Appointment existing : draft.getAppointments()) draft.removeAppointment(existing);
        List<Map<String, Object>> appointments = (List<Map<String, Object>>) input.get("appointments");
        Map<String, Appointment> appointmentByClientId = new LinkedHashMap<>();
        for (int i = 0; i < appointments.size(); i++)
        {
            Map<String, Object> ai = appointments.get(i);
            Appointment a = buildAppointment(ai, path + ".input.appointments[" + i + "]");
            draft.addAppointment(a);
            String aClientId = (String) ai.get("id");
            if (aClientId != null) appointmentByClientId.put(aClientId, a);
            appointmentByClientId.put(a.getId(), a);
        }
        for (Allocatable existing : draft.getAllocatables()) draft.removeAllocatable(existing);
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) input.get("allocations");
        if (allocations != null)
        {
            applyAllocations(draft, allocations, appointmentByClientId, caller,
                    path + ".input.allocations");
        }
        return draft;
    }

    // ============================================================ §12 + result builders

    private User requireCaller()
    {
        User caller = resolveCaller();
        if (caller == null)
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "caller",
                    "Mutations require an authenticated caller");
        }
        return caller;
    }

    private User resolveCaller()
    {
        return jwtUserResolver.resolveCurrentUserOrNull();
    }

    private void requireCanCreate(DynamicType dt, User caller)
    {
        if (caller == null || caller.isAdmin()) return;
        PermissionController pc = operator.getPermissionController();
        // For DynamicType creation we use canRead as the gate — admin-only types are not readable to non-admins
        if (!pc.canRead(dt, caller))
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "typeKey",
                    "No permission to create reservations of type " + dt.getKey());
        }
    }

    private void requireCanModify(Reservation r, User caller)
    {
        if (caller != null && caller.isAdmin()) return;
        PermissionController pc = operator.getPermissionController();
        if (!pc.canModify(r, caller))
        {
            // §12 (security-audit A0d): a caller who can neither read nor modify must not be able
            // to distinguish this from a nonexistent id — report REFERENCE_NOT_FOUND, identical to
            // the null-resolve case. A readable-but-unmodifiable reservation keeps PERMISSION_DENIED
            // (the caller already knows it exists).
            if (!pc.canRead(r, caller))
            {
                throw new ReservationMutationException("REFERENCE_NOT_FOUND", "id",
                        "Reservation " + r.getId() + " not found");
            }
            throw new ReservationMutationException("PERMISSION_DENIED", "id",
                    "No modify permission on reservation " + r.getId());
        }
    }

    private void requireCanRead(Reservation r, User caller)
    {
        if (caller != null && caller.isAdmin()) return;
        PermissionController pc = operator.getPermissionController();
        if (!pc.canRead(r, caller))
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "id",
                    "No read permission on reservation " + r.getId());
        }
    }

    private static Map<String, Object> bulkEntry(int index, Reservation reservation,
            String deletedKind, String deletedId, List<Map<String, Object>> errors)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("index", index);
        if (reservation != null) entry.put("reservation", reservation);
        if (deletedKind != null) entry.put("deletedKind", deletedKind);
        if (deletedId != null) entry.put("deletedId", deletedId);
        entry.put("errors", errors != null ? errors : List.of());
        return entry;
    }

    private static Map<String, Object> bulkResult(String status, List<Map<String, Object>> results)
    {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overallStatus", status);
        out.put("results", results);
        return out;
    }

    /** Translates internally to a GraphQL ValidationError shape via the exception resolver. */
    public static class ReservationMutationException extends RuntimeException
    {
        private final String code;
        private final String path;

        public ReservationMutationException(String code, String path, String message)
        {
            super(message);
            this.code = code;
            this.path = path;
        }

        public String code() { return code; }
        public String path() { return path; }
    }
}
