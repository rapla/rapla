package org.rapla.storage;

import java.time.LocalDateTime;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentMapping;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

import java.util.Collection;
import java.util.Map;

/** Sync sibling of {@link StorageOperator}. Implemented only by in-process server-side
 *  operators (e.g. {@code LocalAbstractCachableOperator}); not by network operators like
 *  {@code RemoteOperator}. Server callers that previously used
 *  {@code SynchronizedCompletablePromise.waitFor(operator.fooAsync())} can inject this
 *  interface and call {@code operator.fooSync()} directly.
 *
 *  <p>Only a subset of {@link StorageOperator}'s async methods is mirrored here — the ones
 *  with active {@code .waitFor} call sites in REST controllers, servlets, and HTML pages.
 *  Add more methods as new sync call sites need them.
 *
 *  <p>Methods are suffixed with {@code Sync} so they don't clash with the async siblings on
 *  classes that implement both interfaces. The pattern in implementations is "async delegates
 *  to sync" — async impls are thin {@code scheduler.supply(() -> syncMethod(...))} wrappers
 *  so there's exactly one source of truth for the work. */
public interface SyncStorageOperator
{
    /**
     * B3: true when this user should be nagged to set a password on login — i.e. their
     * stored password is "unset" (empty/never set) AND they are not the
     * {@code rapla.fix-admin-password}-locked admin (whose credential is intentionally fixed).
     */
    boolean isPasswordChangeRequired(User user) throws RaplaException;

    /**
     * B3: true when the built-in {@code admin} account exists and has an empty
     * (login-with-empty) password. Drives the login-page "default admin / empty password"
     * hint — shown <em>only</em> while that default is still in effect (also under
     * {@code rapla.fix-admin-password}, where it is the demo's login instruction).
     */
    boolean isAdminPasswordUnset() throws RaplaException;

    /** Determines all conflicts the user can modify. If no user is passed, all conflicts are returned. */
    Collection<Conflict> getConflictsSync(User user) throws RaplaException;

    /** Determines all conflicts that involve the given reservation. */
    Collection<Conflict> getConflictsSync(Reservation reservation) throws RaplaException;

    /** Returns appointments matching the given query. */
    AppointmentMapping queryAppointmentsSync(User user, Collection<Allocatable> allocatables, Collection<User> owners,
                                             LocalDateTime start, LocalDateTime end, ClassificationFilter[] filters,
                                             Map<String, String> annotationQuery, boolean requestsOnly) throws RaplaException;

    /** Template-id variant — mirrors {@link StorageOperator#queryAppointments(User, Collection, Collection, Date, Date, ClassificationFilter[], String)}. */
    AppointmentMapping queryAppointmentsSync(User user, Collection<Allocatable> allocatables, Collection<User> owners,
                                             LocalDateTime start, LocalDateTime end, ClassificationFilter[] filters, String templateId) throws RaplaException;


    /** First-allocatable-bindings query — for each allocatable, the appointments that already use it. */
    Map<ReferenceInfo<Allocatable>, Collection<Appointment>> getFirstAllocatableBindingsSync(
            Collection<Allocatable> allocatables, Collection<Appointment> appointments,
            Collection<Reservation> ignoreList) throws RaplaException;

    /** All-allocatable-bindings query — for each allocatable, the full conflict map. */
    Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> getAllAllocatableBindingsSync(
            Collection<Allocatable> allocatables, Collection<Appointment> appointments,
            Collection<Reservation> ignoreList) throws RaplaException;

    /** Merge several allocatables into one. */
    Allocatable doMergeSync(Allocatable selectedObject, java.util.Set<ReferenceInfo<Allocatable>> allocatableIds, User user) throws RaplaException;

    /** Find the next free allocation slot for the given appointment. */
    LocalDateTime getNextAllocatableDateSync(Collection<Allocatable> allocatables, Appointment appointment, Collection<Reservation> ignoreList,
                                    Integer worktimeStartMinutes, Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour) throws RaplaException;

    /** Resolve a set of entity references in one batch. Used by {@code SyncCalendarModel}'s
     *  conflict-/request-based query paths to fetch reservations by ID. */
    <T extends org.rapla.entities.Entity> Map<ReferenceInfo<T>, T> getFromIdSync(Collection<ReferenceInfo<T>> idSet, boolean throwEntityNotFound) throws RaplaException;

    /** Sync analogue of {@code RaplaFacade.getReservationsAsync(...)}.
     *  Translates the {@link AppointmentMapping} to a unique {@code Collection<Reservation>}
     *  using {@link AppointmentMapping#getAllReservations()}. Callers don't need to know about
     *  the underlying mapping shape. Pass {@code null} for any param that should not be used
     *  as a filter; matches the {@code RaplaFacade.getReservations(...)} convention. */
    default Collection<Reservation> getReservationsSync(User user, Allocatable[] allocatables, User[] owners,
                                                        LocalDateTime start, LocalDateTime end, ClassificationFilter[] filters) throws RaplaException
    {
        Collection<Allocatable> allocatablesCol = allocatables != null ? java.util.Arrays.asList(allocatables) : null;
        java.util.List<User> ownersList = owners != null ? java.util.Arrays.asList(owners) : java.util.Collections.emptyList();
        AppointmentMapping mapping = queryAppointmentsSync(user, allocatablesCol, ownersList, start, end, filters, java.util.Collections.emptyMap(), false);
        return mapping.getAllReservations();
    }
}
