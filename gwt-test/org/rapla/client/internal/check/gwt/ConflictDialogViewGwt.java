package org.rapla.client.internal.check.gwt;

import org.rapla.client.TreeFactory;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.dialog.gwt.components.VueTree;
import org.rapla.client.dialog.gwt.components.VueTreeNode;
import org.rapla.client.internal.check.ConflictDialogView;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.util.Collection;

@DefaultImplementation(of=ConflictDialogView.class,context = InjectionContext.gwt)
public class ConflictDialogViewGwt implements ConflictDialogView {

    private final TreeFactory treeFactory;

    @Inject
    public ConflictDialogViewGwt(TreeFactory treeFactory) {
        this.treeFactory = treeFactory;
    }

    public VueComponent getConflictPanel(Collection<Conflict> conflicts) throws RaplaException
    {
        final VueTreeNode conflictModel = (VueTreeNode) treeFactory.createConflictModel(conflicts);
        return new VueTree(conflictModel);
    }
}
