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
}
