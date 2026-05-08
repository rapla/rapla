package org.rapla.facade;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentMapping;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaException;

import java.util.Collection;
import java.util.List;

/** Sync sibling of {@link CalendarModel}'s 4 query methods. Implemented by {@code CalendarModelImpl}
 *  when the underlying {@code StorageOperator} also implements {@code SyncStorageOperator}
 *  (i.e. server-side, in-process — {@code LocalAbstractCachableOperator}). On the client side
 *  the storage is {@code RemoteOperator}, which does network I/O and cannot serve sync queries;
 *  client callers must keep using {@link CalendarModel}'s {@code Promise<T>} methods.
 *
 *  <p>Mirror methods 1:1 with {@link CalendarModel#queryReservations}, {@link CalendarModel#queryAppointments},
 *  {@link CalendarModel#queryAppointmentBindings}, {@link CalendarModel#queryBlocks} — same args, raw return,
 *  {@code throws RaplaException}. Pattern matches {@code SyncStorageOperator} (PRD 008 phases 3, 5, 7, 8). */
public interface SyncCalendarModel
{
    Collection<Reservation> queryReservationsSync(TimeInterval interval) throws RaplaException;

    Collection<Appointment> queryAppointmentsSync(TimeInterval interval) throws RaplaException;

    AppointmentMapping queryAppointmentBindingsSync(TimeInterval interval) throws RaplaException;

    List<AppointmentBlock> queryBlocksSync(TimeInterval timeInterval) throws RaplaException;
}
