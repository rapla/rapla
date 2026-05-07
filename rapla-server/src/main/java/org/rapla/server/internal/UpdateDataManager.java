package org.rapla.server.internal;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.storage.UpdateEvent;

import java.util.Date;

/**
 * Creates an update event with all resources that should be synced for the user
 */
public interface UpdateDataManager
{
    UpdateEvent createUpdateEvent(User user, Date lastSynced) throws RaplaException;
    UpdateEvent createUpdateEventReservations(User user, Date lastSynced) throws RaplaException;

    /** {@code LocalDateTime} variants — distinct names. */
    default UpdateEvent createUpdateEventLocalDateTime(User user, java.time.LocalDateTime lastSynced) throws RaplaException {
        return createUpdateEvent(user, lastSynced == null ? null : org.rapla.components.util.DateTools.toDate(lastSynced));
    }
    default UpdateEvent createUpdateEventReservationsLocalDateTime(User user, java.time.LocalDateTime lastSynced) throws RaplaException {
        return createUpdateEventReservations(user, lastSynced == null ? null : org.rapla.components.util.DateTools.toDate(lastSynced));
    }
}
