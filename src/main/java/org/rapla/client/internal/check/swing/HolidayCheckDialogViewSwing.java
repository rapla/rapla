package org.rapla.client.internal.check.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.internal.check.ConflictDialogView;
import org.rapla.client.internal.check.HolidayCheckDialogView;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.TreeModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@DefaultImplementation(of= HolidayCheckDialogView.class,context = InjectionContext.swing)
public class HolidayCheckDialogViewSwing implements HolidayCheckDialogView
{
    RaplaResources i18n;
    @Inject
    public HolidayCheckDialogViewSwing(RaplaResources i18n) {
        this.i18n = i18n;
    }
    @Override
    public HolidayCheckPanel getConflictPanel(RaplaTreeNode root, boolean showCheckbox)
    {
        final HolidayCheckPanel result = new HolidayCheckPanel();
        JPanel panel = new JPanel();
        BorderLayout layout = new BorderLayout();
        panel.setLayout(layout);


        TreeModel treeModel = new RaplaSwingTreeModel(root);
        RaplaTree treeSelection = new RaplaTree();
        JTree tree = treeSelection.getTree();
        //tree.setCellRenderer( treeF);
        tree.setRootVisible(false);
        treeSelection.setMultiSelect(true);
        tree.setShowsRootHandles(true);
        //tree.setCellRenderer(treeFactory.createConflictRenderer());
        treeSelection.exchangeTreeModel(treeModel);
        treeSelection.expandAll();
        treeSelection.setPreferredSize(new Dimension(400, 200));
        panel.add(BorderLayout.CENTER, treeSelection);
        final int childCount = root.getChildCount();
        List selected = new ArrayList();
        for ( int i=0;i<childCount;i++)
        {
            selected.add(root.getChild(i).getUserObject());
        }

        if ( showCheckbox )
        {
            final JCheckBox ausnahmenCheck = new JCheckBox(i18n.getString("appointment.exceptions.add"));
            ausnahmenCheck.setSelected(true);
            result.checked = true;
            ausnahmenCheck.addChangeListener((e) -> {
                result.checked = ausnahmenCheck.isSelected();
            });
            panel.add(BorderLayout.SOUTH, ausnahmenCheck);
        } else {
            result.checked = false;
        }
        treeSelection.select(selected);
        result.selectedItems = new HashSet(treeSelection.getSelectedElements());
        tree.getSelectionModel().addTreeSelectionListener(e -> {
            result.selectedItems = new HashSet(treeSelection.getSelectedElements());
        });
        result.component = panel;
        return result;
    }

}
