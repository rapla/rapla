package org.rapla.client.internal.check.swing;

import org.rapla.client.internal.check.ConflictDialogView;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.internal.view.ConflictTreeCellRenderer;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
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

    private final ConflictTreeCellRenderer conflictTreeCellRenderer;

    @Inject
    public ConflictDialogViewSwing(TreeFactory treeFactory, ConflictTreeCellRenderer conflictTreeCellRenderer) {
        this.treeFactory = treeFactory;
        this.conflictTreeCellRenderer = conflictTreeCellRenderer;
    }

    public Object getConflictPanel(Collection<Conflict> conflicts) throws RaplaException {
        TreeModel treeModel = new RaplaSwingTreeModel(treeFactory.createConflictModel( conflicts));
        RaplaTree treeSelection = new RaplaTree();
        JTree tree = treeSelection.getTree();
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(conflictTreeCellRenderer);
        treeSelection.exchangeTreeModel(treeModel);
        treeSelection.expandAll();
        treeSelection.setPreferredSize( new Dimension(400,200));
        return treeSelection;
    }
}
