package org.rapla.facade.server;

import org.rapla.entities.Entity;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;

public interface RaplaServerFacade extends RaplaFacade {
    /** Returns the persistant version of a working copyReservations.
     * Throws an {@link org.rapla.entities.EntityNotFoundException} when the
     * object is not found
     * @see #edit
     * @see #clone
     */
    <T extends Entity> T getPersistant(T working) throws RaplaException;
}
