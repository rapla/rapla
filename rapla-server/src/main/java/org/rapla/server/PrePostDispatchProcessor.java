package org.rapla.server;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.UpdateEvent;

@ExtensionPoint(context = InjectionContext.server,id = PrePostDispatchProcessor.ID)
public interface PrePostDispatchProcessor
{
    String ID = "org.rapla.server.prePostDispatch";

    void preProcess(User sessionUser, UpdateEvent evt) throws RaplaException;

    void postProcess(User sessionUser, UpdateEvent result) throws RaplaException;
}
