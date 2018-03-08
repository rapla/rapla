package org.rapla.client.internal.check.gwt;

import org.rapla.client.internal.check.ConflictDialogView;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;

@DefaultImplementation(of=ConflictDialogView.class,context = InjectionContext.gwt)
public class ConflictDialogViewGwt implements ConflictDialogView {

    @Inject
    public ConflictDialogViewGwt() {

    }

    public Object getConflictPanel(Collection<Conflict> conflicts)  {
        // FIXME createInfoDialog conflictList
        return null;
    }
}
