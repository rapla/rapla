/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.gui.internal;

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultTreeModel;

import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.MenuContext;
import org.rapla.gui.MenuFactory;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.internal.action.RaplaObjectAction;
import org.rapla.gui.internal.common.InternMenus;
import org.rapla.gui.internal.common.MultiCalendarView;
import org.rapla.gui.internal.edit.ClassifiableFilterEdit;
import org.rapla.gui.internal.view.TreeFactoryImpl;
import org.rapla.gui.toolkit.PopupEvent;
import org.rapla.gui.toolkit.PopupListener;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaPopupMenu;
import org.rapla.gui.toolkit.RaplaTree;
import org.rapla.gui.toolkit.RaplaWidget;

public class ResourceSelection extends RaplaGUIComponent implements RaplaWidget {
    protected JPanel content = new JPanel();
    public RaplaTree treeSelection = new RaplaTree();
    TableLayout tableLayout;
    protected JPanel buttonsPanel = new JPanel();

    protected final CalendarSelectionModel model;
    MultiCalendarView view;
    Listener listener = new Listener();
  
    protected FilterEditButton filterEdit;

	public ResourceSelection(RaplaContext context, MultiCalendarView view, CalendarSelectionModel model) throws RaplaException {
        super(context);

        this.model = model;
        this.view = view;
        /*double[][] sizes = new double[][] { { TableLayout.FILL }, { TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.FILL } };
        tableLayout = new TableLayout(sizes);*/
        content.setLayout(new BorderLayout());

        content.add(treeSelection);
        // content.setPreferredSize(new Dimension(260,400));
        content.setBorder(BorderFactory.createRaisedBevelBorder());
        
        content.add(buttonsPanel, BorderLayout.NORTH);
        
        buttonsPanel.setLayout( new BorderLayout());
        filterEdit = new FilterEditButton(context, model, listener,true);
        buttonsPanel.add(filterEdit.getButton(), BorderLayout.EAST);
        
        treeSelection.setToolTipRenderer(getTreeFactory().createTreeToolTipRenderer());
        treeSelection.setMultiSelect(true);
        treeSelection.getTree().setSelectionModel(((TreeFactoryImpl) getTreeFactory()).createComplexTreeSelectionModel());

        updateTree();
        updateSelection();
        treeSelection.addChangeListener(listener);
        treeSelection.addPopupListener(listener);
        treeSelection.addDoubleclickListeners(listener);
        treeSelection.getTree().addFocusListener(listener);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(treeSelection.getTree());

        updateMenu();
    }
	
    protected HashSet<?> setSelectedObjects(){
        HashSet<Object> elements = new HashSet<Object>(treeSelection.getSelectedElements());
        getModel().setSelectedObjects(elements);
        return elements;
    }
    
    public RaplaArrowButton getFilterButton() {
        return filterEdit.getButton();
    }
	
	public RaplaTree getTreeSelection() {
        return treeSelection;
    }

    protected CalendarSelectionModel getModel() {
        return model;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException {
    	if ( evt != null && evt.isModified())
    	{
    		 updateTree();
    		 updateMenu();
    	}
    	// No longer needed here as directly done in RaplaClientServiceImpl
    	// ((CalendarModelImpl) model).dataChanged( evt);
    }

    final protected TreeFactory getTreeFactory() {
        return getService(TreeFactory.class);
    }

    boolean treeListenersEnabled = true;

    /*
     * (non-Javadoc)
     * 
     * @see org.rapla.gui.internal.view.ITreeFactory#createClassifiableModel(org.rapla.entities.dynamictype.Classifiable[], org.rapla.entities.dynamictype.DynamicType[])
     */
    protected void updateTree() throws RaplaException {

        treeSelection.getTree().setRootVisible(false);
        treeSelection.getTree().setShowsRootHandles(true);
        treeSelection.getTree().setCellRenderer(((TreeFactoryImpl) getTreeFactory()).createRenderer());
        
        DefaultTreeModel treeModel = generateTree();
        try {
            treeListenersEnabled = false;
            treeSelection.exchangeTreeModel(treeModel);
            updateSelection();
        } finally {
            treeListenersEnabled = true;
        }

    }

    protected DefaultTreeModel generateTree() throws RaplaException {
        ClassificationFilter[] filter = getModel().getAllocatableFilter();
        final TreeFactoryImpl treeFactoryImpl = (TreeFactoryImpl) getTreeFactory();
        DefaultTreeModel treeModel = treeFactoryImpl.createModel(filter);
        return treeModel;
    }

    protected void updateSelection() {
        Collection<Object> selectedObjects = new ArrayList<Object>(getModel().getSelectedObjects());
        treeSelection.select(selectedObjects);
    }

    public JComponent getComponent() {
        return content;
    }

    
    protected MenuContext createMenuContext(Point p, Object obj) {
        MenuContext menuContext = new MenuContext(getContext(), obj, getComponent(), p);
        return menuContext;
    }
    
    protected void showTreePopup(PopupEvent evt) {
        try {

            Point p = evt.getPoint();
            Object obj = evt.getSelectedObject();
            List<?> list = treeSelection.getSelectedElements();

            MenuContext menuContext = createMenuContext(p, obj);
            menuContext.setSelectedObjects(list);
            

            RaplaPopupMenu menu = new RaplaPopupMenu();

            RaplaMenu newMenu = new RaplaMenu("new");
            newMenu.setText(getString("new"));
            boolean addNewReservationMenu = obj instanceof Allocatable || obj instanceof DynamicType;
            ((MenuFactoryImpl) getMenuFactory()).addNew(newMenu, menuContext, null, addNewReservationMenu);

            getMenuFactory().addObjectMenu(menu, menuContext, "EDIT_BEGIN");
            newMenu.setEnabled( newMenu.getMenuComponentCount() > 0);
            menu.insertAfterId(newMenu, "EDIT_BEGIN");

            JComponent component = (JComponent) evt.getSource();

            menu.show(component, p.x, p.y);
        } catch (RaplaException ex) {
            showException(ex, getComponent());
        }
    }
    
    class Listener implements PopupListener, ChangeListener, ActionListener, FocusListener {

        public void showPopup(PopupEvent evt) {
            showTreePopup(evt);
        }
     
        public void actionPerformed(ActionEvent evt) {
            Object focusedObject = evt.getSource();
            if ( focusedObject == null || !(focusedObject instanceof RaplaObject))
            	return;
            // System.out.println(focusedObject.toString());
            RaplaType type = ((RaplaObject) focusedObject).getRaplaType();
            if (    type == User.TYPE
                 || type == Allocatable.TYPE
                 || type ==Period.TYPE 
               )
            {
            	
                RaplaObjectAction editAction = new RaplaObjectAction( getContext(), getComponent(),null);
                if (editAction.canModify( focusedObject))
                {
                    editAction.setEdit((Entity<?>)focusedObject);
                    editAction.actionPerformed(null);
                }
            }
        }

        public void stateChanged(ChangeEvent evt) {
            if (!treeListenersEnabled) {
                return;
            }
            try {
            	Object source = evt.getSource();
				ClassifiableFilterEdit filterUI = filterEdit.getFilterUI();
				if ( filterUI != null && source == filterUI)
            	{
            		final ClassificationFilter[] filters = filterUI.getFilters();
            		model.setAllocatableFilter( filters);
            		updateTree();
            		applyFilter();
            	}
            	else if ( source == treeSelection)
                {
            		updateChange();
            	}
            } catch (Exception ex) {
                showException(ex, getComponent());
            }
        }
        
       
		public void focusGained(FocusEvent e) {
			try {
				if (!getUserModule().isSessionActive())
				{
					return;
				}
				updateMenu();
			} catch (Exception ex) {
                showException(ex, getComponent());
            }
		}

		public void focusLost(FocusEvent e) {
		}


    }

    public void updateChange() throws RaplaException {
        setSelectedObjects();
        updateMenu();
        applyFilter();
    }
    
    public void applyFilter() throws RaplaException {
        view.getSelectedCalendar().update();
    }

   
    
    public void updateMenu() throws RaplaException {
        RaplaMenu editMenu = getService(InternMenus.EDIT_MENU_ROLE);
        RaplaMenu newMenu = getService(InternMenus.NEW_MENU_ROLE);

        editMenu.removeAllBetween("EDIT_BEGIN", "EDIT_END");
        newMenu.removeAll();

        List<?> list = treeSelection.getSelectedElements();
        Object focusedObject = null;
        if (list.size() == 1) {
            focusedObject = treeSelection.getSelectedElement();
        }

        MenuContext menuContext = createMenuContext( null,  focusedObject);
        menuContext.setSelectedObjects(list);
        if ( treeSelection.getTree().hasFocus())
        {
        	getMenuFactory().addObjectMenu(editMenu, menuContext, "EDIT_BEGIN");
        }
        ((MenuFactoryImpl) getMenuFactory()).addNew(newMenu, menuContext, null, true);
        newMenu.setEnabled(newMenu.getMenuComponentCount() > 0 );
    }

    public MenuFactory getMenuFactory() {
        return getService(MenuFactory.class);
    }
    
   
}
