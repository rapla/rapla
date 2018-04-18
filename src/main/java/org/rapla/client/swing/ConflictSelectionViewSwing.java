/*--------------------------------------------------------------------------*
 | Copyright (C) 2008  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.TreeFactory;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.ConflictSelectionView;
import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.view.*;
import org.rapla.client.swing.toolkit.PopupEvent;
import org.rapla.client.swing.toolkit.PopupListener;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.util.Collection;

@DefaultImplementation(context = InjectionContext.swing, of = ConflictSelectionView.class)
public class ConflictSelectionViewSwing implements ConflictSelectionView<Component>
{
    private RaplaTree treeSelection = new RaplaTree();
    protected JPanel content = new JPanel();
    JLabel summary = new JLabel();
    private final Listener listener = new Listener();
    private final TreeFactory treeFactory;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaResources i18n;
    private Presenter presenter;

    @Inject
    public ConflictSelectionViewSwing(RaplaResources i18n,  Logger logger, TreeFactory treeFactory,
                                      DialogUiFactoryInterface dialogUiFactory, ConflictTreeCellRenderer treeCellRenderer) throws RaplaInitializationException
    {
        this.i18n = i18n;
        this.treeFactory = treeFactory;
        this.dialogUiFactory = dialogUiFactory;
        final JTree navTree = treeSelection.getTree();
        content.setLayout(new BorderLayout());

        content.add(treeSelection);
        // content.setPreferredSize(new Dimension(260,400));
        content.setBorder(BorderFactory.createRaisedBevelBorder());
        JTree tree = treeSelection.getTree();
        //tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(treeCellRenderer);
        tree.setSelectionModel(new DelegatingTreeSelectionModel(this::isSelectable));
        treeSelection.addPopupListener(listener);
        navTree.addTreeSelectionListener(listener);
    }

    boolean isSelectable(TreePath treePath)
    {
        Object lastPathComponent = treePath.getLastPathComponent();
        Object object = TreeFactoryImpl.getUserObject(lastPathComponent);
        if (object instanceof Conflict)
        {
            return true;
        }
        if (object instanceof Allocatable)
        {
            return true;
        }
        if (object instanceof DynamicType)
        {
            return true;
        }
        return false;
    }

    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }
    
    protected Presenter getPresenter() {
        return presenter;
    }
    
    
    @Override
    public void showMenuPopup(PopupContext context, boolean enabledButtonEnabled, boolean disableButtonEnabled)
    {
        RaplaPopupMenu menu = new RaplaPopupMenu(context);
        RaplaMenuItem disable = new RaplaMenuItem("disable");
        disable.setText(i18n.getString("disable_conflicts"));
        disable.setEnabled( disableButtonEnabled );
        RaplaMenuItem enable = new RaplaMenuItem("enable");
        enable.setText(i18n.getString("enable_conflicts"));
        enable.setEnabled( enabledButtonEnabled );

        disable.addActionListener(e -> {
            PopupContext context12 = new SwingPopupContext(disable, null);
            getPresenter().disableConflicts(context12);
        });

        enable.addActionListener(e -> {
            PopupContext context1 = new SwingPopupContext(enable, null);
            getPresenter().enableConflicts(context1);
        });

        menu.add(disable);
        menu.add(enable);
        JComponent component = (JComponent) SwingPopupContext.extractParent(context);
        final Point p = SwingPopupContext.extractPoint(context);
        menu.show(component, p.x, p.y);
    }

    class Listener implements TreeSelectionListener, PopupListener
    {
        public void valueChanged(TreeSelectionEvent e)
        {
            PopupContext context = new SwingPopupContext(treeSelection, null);
            getPresenter().showConflicts(context);
        }

        @Override
        public void showPopup(PopupEvent evt)
        {
            final Point point = evt.getPoint();
            PopupContext context = new SwingPopupContext(treeSelection, point);
            getPresenter().showTreePopup(context);
        }
    }

    public RaplaTree getTreeSelection()
    {
        return treeSelection;
    }

    @Override
    public void redraw()
    {
        treeSelection.repaint();
    }

    public JComponent getComponent()
    {
        return content;
    }

    final protected TreeFactory getTreeFactory()
    {
        return treeFactory;
    }

    @Override
    public Collection<Object> getSelectedElements(boolean withChilds)
    {
        return treeSelection.getSelectedElements(withChilds);
    }

    @Override
    public void updateTree(Collection<Conflict> selectedConflicts, Collection<Conflict> conflicts)
    {
        TreeModel treeModel;
        try
        {
            final RaplaTreeNode conflictModel = getTreeFactory().createConflictModel(conflicts);
            treeModel = new RaplaSwingTreeModel( conflictModel);
        }
        catch (RaplaException e)
        {
            dialogUiFactory.showException(e, new SwingPopupContext(getComponent(), null));
            return;
        }
        SwingUtilities.invokeLater( ()->
                {
                    treeSelection.exchangeTreeModel(treeModel);
                    treeSelection.getTree().expandRow(0);
                    summary.setText(i18n.getString("conflicts") + " (" + conflicts.size() + ") ");
                }
        );
    }

    public void clearSelection()
    {
        treeSelection.getTree().setSelectionPaths(new TreePath[] {});
    }

    @Override
    public Component getSummary()
    {
        return summary;
    }

}
