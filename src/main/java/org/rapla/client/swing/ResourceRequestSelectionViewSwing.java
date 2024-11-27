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
import org.rapla.client.internal.ResourceRequestSelectionView;
import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.view.ConflictTreeCellRenderer;
import org.rapla.client.swing.internal.view.DelegatingTreeSelectionModel;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
import org.rapla.client.swing.internal.view.RequestTreeCellRenderer;
import org.rapla.client.swing.toolkit.*;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collection;

@DefaultImplementation(context = InjectionContext.swing, of =  ResourceRequestSelectionView.class)
public class ResourceRequestSelectionViewSwing implements ResourceRequestSelectionView<Component>
{
    private final RaplaTree treeSelection = new RaplaTree();
    private boolean selectionFromProgram = false;
    protected JPanel content = new JPanel();
    JLabel summary = new JLabel();
    private final Listener listener = new Listener();
    private final TreeFactory treeFactory;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaResources i18n;
    private Presenter presenter;

    @Inject
    public ResourceRequestSelectionViewSwing(RaplaResources i18n, Logger logger, TreeFactory treeFactory,
                                             DialogUiFactoryInterface dialogUiFactory, RequestTreeCellRenderer treeCellRenderer) throws RaplaInitializationException
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
        treeSelection.addChangeListener((evt) ->
        {
            if ( selectionFromProgram )
            {
                return;
            }
            getPresenter().treeSelectionChanged();
        });
        treeSelection.addPopupListener(listener);
        navTree.addTreeSelectionListener(listener);
    }

    boolean isSelectable(TreePath treePath)
    {
        Object lastPathComponent = treePath.getLastPathComponent();
        Object object = TreeFactoryImpl.getUserObject(lastPathComponent);
        if (object instanceof Reservation)
        {
            return true;
        }
        if (object instanceof Allocatable)
        {
            return true;
        }
        return object instanceof DynamicType;
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
        JComponent component = (JComponent) SwingPopupContext.extractParent(context);
        final Point p = SwingPopupContext.extractPoint(context);
        menu.show(component, p.x, p.y);
    }

    class Listener implements TreeSelectionListener, PopupListener
    {
        public void valueChanged(TreeSelectionEvent e)
        {
            PopupContext context = new SwingPopupContext(treeSelection, null);
            getPresenter().showRequests(context);
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
    public void updateTree(Collection<Reservation> selectedRequests, Collection<Reservation> requests)
    {
        TreeModel treeModel;
        try
        {
            final RaplaTreeNode requestModel = getTreeFactory().createResourceRequestModel(requests);
            treeModel = new RaplaSwingTreeModel( requestModel);
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
                    summary.setText(i18n.getString("conflicts") + " (" + requests.size() + ") ");
                }
        );
    }

    public void clearSelection()
    {
        try {
            selectionFromProgram = true;
            treeSelection.getTree().setSelectionPaths(new TreePath[]{});
        } finally {
            selectionFromProgram = false;
        }
    }

    @Override
    public Component getSummary()
    {
        return summary;
    }

}
