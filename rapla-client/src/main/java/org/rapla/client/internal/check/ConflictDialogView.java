package org.rapla.client.internal.check;

import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

import java.util.Collection;

public interface ConflictDialogView {
    Object getConflictPanel(Collection<Conflict> conflicts) throws RaplaException;
}
