package org.rapla.client.internal.check.swing;

import org.rapla.client.internal.check.ConflictDialogView;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.util.Collection;

@DefaultImplementation(of=ConflictDialogView.class,context = InjectionContext.swing)
public class ConflictDialogViewSwing implements ConflictDialogView {
    private final TreeFactory treeFactory;

    @Inject
    public ConflictDialogViewSwing(TreeFactory treeFactory) {
        this.treeFactory = treeFactory;
    }

    public Object getConflictPanel(Collection<Conflict> conflicts) throws RaplaException {
        TreeModel treeModel = treeFactory.createConflictModel( conflicts);
        RaplaTree treeSelection = new RaplaTree();
        JTree tree = treeSelection.getTree();
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(treeFactory.createConflictRenderer());
        treeSelection.exchangeTreeModel(treeModel);
        treeSelection.expandAll();
        treeSelection.setPreferredSize( new Dimension(400,200));
        return treeSelection;
    }
}
