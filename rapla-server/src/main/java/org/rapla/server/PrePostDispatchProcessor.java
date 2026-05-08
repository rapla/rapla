package org.rapla.server;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.storage.UpdateEvent;


public interface PrePostDispatchProcessor
{
    String ID = "org.rapla.server.prePostDispatch";

    void preProcess(User sessionUser, UpdateEvent evt) throws RaplaException;

    void postProcess(User sessionUser, UpdateEvent result) throws RaplaException;
}
