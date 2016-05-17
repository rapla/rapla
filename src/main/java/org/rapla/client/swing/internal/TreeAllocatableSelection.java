/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.client.swing.internal;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class TreeAllocatableSelection extends RaplaGUIComponent implements ChangeListener {
    JPanel content= new JPanel();
    RaplaTree treeSelection;
    JPanel buttonPanel;
    JButton deleteButton;
    JButton addButton;
    NotificationAction deleteAction;
    NotificationAction addAction;
    String addDialogTitle;
    private final TreeFactory treeFactory;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
	public TreeAllocatableSelection(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.treeFactory = treeFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        treeSelection = new RaplaTree();
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(Color.white,new Color(178, 178, 178)),getString("selection_resource"));
		content.setBorder(border);
        buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        deleteButton = new JButton();
        addButton = new JButton();
        buttonPanel.add(addButton);
        buttonPanel.add(deleteButton);
        content.setLayout(new BorderLayout());
        content.add(buttonPanel, BorderLayout.NORTH);
        content.add(treeSelection, BorderLayout.CENTER);

        deleteAction = new NotificationAction().setDelete();
        addAction = new NotificationAction().setAdd();
        deleteButton.setAction(deleteAction);
        addButton.setAction(addAction);
        treeSelection.addChangeListener(this);
        treeSelection.getTree().setCellRenderer(treeFactory.createRenderer());
        treeSelection.getTree().setModel( treeFactory.createClassifiableModel( Allocatable.ALLOCATABLE_ARRAY, false));
        addDialogTitle = getString( "add") ;
     }
    
    Set<Allocatable> allocatables = new TreeSet<Allocatable>(new NamedComparator<Allocatable>(getLocale()));


    public JComponent getComponent() {
        return content;
    }

    final private TreeFactory getTreeFactory() {
        return  treeFactory;
    }

    public void setAllocatables(Collection<Allocatable> list)  {
        allocatables.clear();
        if ( list != null ){
            allocatables.addAll(list);
        } 
        update();
    }
    
    public Collection<Allocatable> getAllocatables() {
		return allocatables;
	}

    private void update() {
        TreeFactory treeFactory = getTreeFactory();
        TreeModel model = treeFactory.createClassifiableModel(allocatables.toArray(Allocatable.ALLOCATABLE_ARRAY), false);
		treeSelection.exchangeTreeModel(model);
    }

	public void stateChanged(ChangeEvent e) {
        deleteAction.setEnabled(!deleteAction.getSelected().isEmpty());
    }
	

    public String getAddDialogTitle() {
		return addDialogTitle;
	}

	public void setAddDialogTitle(String addDialogTitle) {
		this.addDialogTitle = addDialogTitle;
	}

    class NotificationAction extends AbstractAction {
        private static final long serialVersionUID = 1L;
        
        int ADD = 1;
        int DELETE = 2;
        int type;

        NotificationAction setDelete() {
            putValue(NAME,getString("delete"));
            putValue(SMALL_ICON,raplaImages.getIconFromKey("icon.delete"));
            setEnabled(false);
            type = DELETE;
            return this;
        }

        NotificationAction setAdd() {
            putValue(NAME,getString("add"));
            putValue(SMALL_ICON,raplaImages.getIconFromKey("icon.new"));
            type = ADD;
            return this;
        }

		protected List<Allocatable> getSelected() {
			return getSelectedAllocatables(treeSelection);
		}

		protected List<Allocatable> getSelectedAllocatables(RaplaTree tree) {
			List<Allocatable> allocatables = new ArrayList<Allocatable>();
			List<Object> selectedElements = tree.getSelectedElements();
			for ( Object obj:selectedElements)
			{
				allocatables.add((Allocatable) obj);
			}
			return allocatables;
		}

        public void actionPerformed(ActionEvent evt) {
            try {
                if (type == DELETE) {
                	allocatables.removeAll( getSelected());
                	update();
                } else if (type == ADD) {
                    showAddDialog();
                }
            } catch (Exception ex) {
                dialogUiFactory.showException(ex,new SwingPopupContext(getComponent(), null));
            }
        }

        private void showAddDialog() throws RaplaException {
            final DialogInterface dialog;
            RaplaTree treeSelection = new RaplaTree();
            treeSelection.setMultiSelect(true);
            treeSelection.getTree().setCellRenderer(getTreeFactory().createRenderer());

            treeSelection.exchangeTreeModel(getTreeFactory().createClassifiableModel(getQuery().getAllocatables(),true));
            treeSelection.setMinimumSize(new java.awt.Dimension(300, 200));
            treeSelection.setPreferredSize(new java.awt.Dimension(400, 260));
            dialog = dialogUiFactory.create(
                    new SwingPopupContext(getComponent(), null)
                    ,true
                    ,treeSelection
                    ,new String[] { getString("add"),getString("cancel")});
            dialog.setTitle(addDialogTitle);
            dialog.getAction(0).setEnabled(false);
            
            final JTree tree = treeSelection.getTree();
            tree.addMouseListener(new MouseAdapter() {
                // End dialog when a leaf is double clicked
                public void mousePressed(MouseEvent e) {
                    TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                    if (selPath != null && e.getClickCount() == 2) {
                        final Object lastPathComponent = selPath.getLastPathComponent();
                        if (((TreeNode) lastPathComponent).isLeaf() ) {
                            dialog.getAction(0).execute();
                            return;
                        }
                    }
                    else
                    	if (selPath != null && e.getClickCount() == 1) {
	                        final Object lastPathComponent = selPath.getLastPathComponent();
	                        if (((TreeNode) lastPathComponent).isLeaf() ) {
	                            dialog.getAction(0).setEnabled(true);
	                            return;
	                        }
                    	}
	                tree.removeSelectionPath(selPath);
                }
            });
            
            dialog.start(true); 
            if (dialog.getSelectedIndex() == 0) {
                List<Allocatable> selected = getSelectedAllocatables(treeSelection);
                allocatables.addAll(selected);
                update();
            }
        }
    }
}
