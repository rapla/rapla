/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.gui.internal.edit.reservation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI;
import javax.swing.plaf.basic.BasicRadioButtonMenuItemUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.rapla.components.layout.TableLayout;
import org.rapla.components.treetable.AbstractTreeTableModel;
import org.rapla.components.treetable.JTreeTable;
import org.rapla.components.treetable.TableToolTipRenderer;
import org.rapla.components.treetable.TreeTableModel;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.Named;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AppointmentListener;
import org.rapla.gui.MenuContext;
import org.rapla.gui.MenuFactory;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.internal.FilterEditButton;
import org.rapla.gui.internal.MenuFactoryImpl;
import org.rapla.gui.internal.common.CalendarAction;
import org.rapla.gui.internal.edit.ClassifiableFilterEdit;
import org.rapla.gui.toolkit.PopupEvent;
import org.rapla.gui.toolkit.PopupListener;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.gui.toolkit.RaplaColorList;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaPopupMenu;
import org.rapla.gui.toolkit.RaplaSeparator;
import org.rapla.gui.toolkit.RaplaWidget;

/**
 * <p>
 * GUI for editing the allocations of a reservation. Presents two TreeTables. The left one displays
 * all available Resources and Persons the right one all selected Resources and Persons.
 * </p>
 * <p>
 * The second column of the first table contains information about the availability on the
 * appointments of the reservation. In the second column of the second table the user can add
 * special Restrictions on the selected Resources and Persons.
 * </p>
 * 
 * @see Reservation
 * @see Allocatable
 */
public class AllocatableSelection extends RaplaGUIComponent implements AppointmentListener, PopupListener, RaplaWidget
{
	JSplitPane				content			= new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	JPanel					leftPanel		= new JPanel();
	JTreeTable				completeTable;
	RaplaButton				btnAdd			= new RaplaButton(RaplaButton.SMALL);
	RaplaButton				btnCalendar1	= new RaplaButton(RaplaButton.SMALL);
	JPanel					rightPanel		= new JPanel();
	JTreeTable				selectedTable;
	RaplaButton				btnRemove		= new RaplaButton(RaplaButton.SMALL);
	RaplaButton				btnCalendar2	= new RaplaButton(RaplaButton.SMALL);
	
	Reservation				mutableReservation;
	Reservation             originalReservation;
	
	
	AllocatablesModel		completeModel	= new CompleteModel();
	AllocatablesModel		selectedModel	= new SelectedModel();
	
	Map<Allocatable, Collection<Appointment>> allocatableBindings = new HashMap<Allocatable, Collection<Appointment>>();
//	Map<Appointment,Collection<Allocatable>> appointmentMap	= new HashMap<Appointment,Collection<Allocatable>>();
	Appointment[]			appointments;
	String[]				appointmentStrings;
	String[]				appointmentIndexStrings;
	
	CalendarSelectionModel	calendarModel;
	EventListenerList		listenerList	= new EventListenerList();
	Listener				listener		= new Listener();
	
	//FilterAction			filterAction;
	AllocatableAction		addAction;
	AllocatableAction		removeAction;
	AllocatableAction		calendarAction1;
	AllocatableAction		calendarAction2;
	
	User					user;
	
	CommandHistory commandHistory;

	FilterEditButton filter;
	
	public AllocatableSelection(RaplaContext sm)  
	{
		this(sm, false, new CommandHistory());
	}

	public AllocatableSelection(RaplaContext sm, boolean addCalendarButton,CommandHistory commandHistory)  
	{
		super(sm);
		// Undo Command History
		this.commandHistory = commandHistory;
		double pre = TableLayout.PREFERRED;
		double fill = TableLayout.FILL;
		double tableSize[][] = { { pre, 12, pre, 3, fill, pre}, // Columns
		{ pre, fill } }; // Rows
		leftPanel.setLayout(new TableLayout(tableSize));
		
		if (addCalendarButton)
			leftPanel.add(btnCalendar1, "0,0,l,f");
		leftPanel.add(btnAdd, "5,0,r,f");
		rightPanel.setLayout(new TableLayout(tableSize));
		rightPanel.add(btnRemove, "0,0,l,f");
		if (addCalendarButton)
			rightPanel.add(btnCalendar2, "2,0,c,c");
		content.setLeftComponent(leftPanel);
		content.setRightComponent(rightPanel);
		content.setResizeWeight(0.3);
		
		btnAdd.setEnabled(false);
		btnCalendar1.setEnabled(false);
		btnRemove.setEnabled(false);
		btnCalendar2.setEnabled(false);
		
		addAction = new AllocatableAction("add");
		removeAction = new AllocatableAction("remove");
		calendarAction1 = new AllocatableAction("calendar1");
		calendarAction2 = new AllocatableAction("calendar2");
		
		btnAdd.setAction(addAction);
		btnRemove.setAction(removeAction);
		btnCalendar1.setAction(calendarAction1);
		btnCalendar2.setAction(calendarAction2);
		
		completeTable = new JTreeTable(completeModel);
		Color tableBackground = completeTable.getTree().getBackground();
		JScrollPane leftScrollpane = new JScrollPane(completeTable);
		leftScrollpane.getViewport().setBackground(tableBackground);
		leftPanel.add(leftScrollpane, "0,1,5,1,f,f");
		completeTable.setGridColor(RaplaColorList.darken(tableBackground, 20));
		completeTable.setToolTipRenderer(new RaplaToolTipRenderer());
		completeTable.getSelectionModel().addListSelectionListener(listener);
		completeTable.setDefaultRenderer(Allocatable.class, new AllocationCellRenderer());
		completeTable.setDefaultEditor(Allocatable.class, new AppointmentCellEditor2(new AllocationTextField()));
		completeTable.getTree().setCellRenderer(new AllocationTreeCellRenderer(false));
		completeTable.addMouseListener(listener);
		
		selectedTable = new JTreeTable(selectedModel);
		JScrollPane rightScrollpane = new JScrollPane(selectedTable);
		rightScrollpane.getViewport().setBackground(tableBackground);
		rightPanel.add(rightScrollpane, "0,1,5,1,f,f");
		selectedTable.setToolTipRenderer(new RaplaToolTipRenderer());
		selectedTable.getSelectionModel().addListSelectionListener(listener);
		selectedTable.setGridColor(RaplaColorList.darken(tableBackground, 20));
		selectedTable.setDefaultRenderer(Appointment[].class, new RestrictionCellRenderer());
		AppointmentCellEditor appointmentCellEditor = new AppointmentCellEditor(new RestrictionTextField());
		selectedTable.setDefaultEditor(Appointment[].class, appointmentCellEditor);
		selectedTable.addMouseListener(listener);
		selectedTable.getTree().setCellRenderer(new AllocationTreeCellRenderer(true));
		
		completeTable.getColumnModel().getColumn(0).setMinWidth(60);
		completeTable.getColumnModel().getColumn(0).setPreferredWidth(120);
		completeTable.getColumnModel().getColumn(1).sizeWidthToFit();
		selectedTable.getColumnModel().getColumn(0).setMinWidth(60);
		selectedTable.getColumnModel().getColumn(0).setPreferredWidth(120);
		selectedTable.getColumnModel().getColumn(1).sizeWidthToFit();
		content.setDividerLocation(0.3);
		
		CalendarSelectionModel originalModel = getService(CalendarSelectionModel.class);
		calendarModel =  originalModel.clone();
		filter = new FilterEditButton( sm, calendarModel, listener,true);
        leftPanel.add(filter.getButton(), "4,0,r,f");
//		filterAction = new FilterAction(getContext(), getComponent(), null);
//		filterAction.setFilter(calendarModel);
//		filterAction.setResourceOnly(true);
	}
	
	public void addChangeListener(ChangeListener listener)
	{
		listenerList.add(ChangeListener.class, listener);
	}
	
	public void removeChangeListener(ChangeListener listener)
	{
		listenerList.remove(ChangeListener.class, listener);
	}
	
	final private TreeFactory getTreeFactory()
	{
		return getService(TreeFactory.class);
	}
	
	protected void fireAllocationsChanged()
	{
		ChangeEvent evt = new ChangeEvent(this);
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length - 2; i >= 0; i -= 2)
		{
			if (listeners[i] == ChangeListener.class)
			{
				((ChangeListener) listeners[i + 1]).stateChanged(evt);
			}
		}
	}
	
	public void dataChanged(ModificationEvent evt) throws RaplaException
	{
		calendarModel.dataChanged( evt);
		boolean updateBindings = false;
		if (evt.isModified(Allocatable.TYPE))
		{
			updateBindings = true;
			Collection<Allocatable> allAllocatables = getAllAllocatables();
			completeModel.setAllocatables(allAllocatables, completeTable.getTree());
			for ( Allocatable allocatable:selectedModel.getAllocatables())
			{
				if ( !allAllocatables.contains(allocatable ))
				{
					mutableReservation.removeAllocatable( allocatable);
				}
			}
			selectedModel.setAllocatables(Arrays.asList(mutableReservation.getAllocatables()), selectedTable.getTree());
			updateButtons();
		}
	
		if (updateBindings || evt.isModified(Reservation.TYPE))
		{
			updateBindings(null);
		}
		
	}
	
	/** Implementation of appointment listener */
	public void appointmentAdded(Collection<Appointment> appointments)
	{
		setAppointments(mutableReservation.getAppointments());
		selectedModel.setAllocatables(getAllocated(), selectedTable.getTree());
		updateBindings( appointments);
	}
	
	public void appointmentChanged(Collection<Appointment> appointments)
	{
		setAppointments(mutableReservation.getAppointments());
		updateBindings( appointments );
	}
	
	public void appointmentSelected(Collection<Appointment> appointments)
	{
	}
	
	private void updateBindings(Collection<Appointment> passedAppointments)
	{
		Collection<Allocatable> allAllocatables = new LinkedHashSet<Allocatable>( completeModel.getAllocatables());
		allAllocatables.addAll(Arrays.asList(mutableReservation.getAllocatables()));
		Collection<Appointment> appointments;
		if ( passedAppointments != null)
		{
			appointments = passedAppointments;
		}
		else
		{
			allocatableBindings.clear();
			for ( Allocatable allocatable: allAllocatables)
			{
				allocatableBindings.put( allocatable, new HashSet<Appointment>());
			}
			appointments = Arrays.asList(mutableReservation.getAppointments());
		}
	
		
		try
		{
			if (!RaplaComponent.isTemplate( this.mutableReservation))
			{
				//      System.out.println("getting allocated resources");

				Map<Allocatable,  Collection<Appointment>> allocatableBindings = getQuery().getAllocatableBindings(allAllocatables,appointments);
				removeFromBindings( appointments);
				for ( Map.Entry<Allocatable,  Collection<Appointment>> entry: allocatableBindings.entrySet())
				{
					Allocatable alloc = entry.getKey();
					Collection<Appointment> list = this.allocatableBindings.get( alloc);
					if ( list == null)
					{
						list = new HashSet<Appointment>();
						this.allocatableBindings.put( alloc, list);
					}
					Collection<Appointment> bindings = entry.getValue();
					list.addAll( bindings);
				}
			}
			//this.allocatableBindings.putAll(allocatableBindings);
			completeModel.treeDidChange();
			selectedModel.treeDidChange();
		}
		catch (RaplaException ex)
		{
			showException(ex, content);
		}
	}
	
	public void appointmentRemoved(Collection<Appointment> appointments)
	{
		removeFromBindings( appointments);
		setAppointments(mutableReservation.getAppointments());
		selectedModel.setAllocatables(getAllocated(), selectedTable.getTree());
		removeFromBindings( appointments);
		List<Appointment> emptyList = Collections.emptyList();
		updateBindings(emptyList);
	}
	
	private void removeFromBindings(Collection<Appointment> appointments) {
		for ( Collection<Appointment> list: allocatableBindings.values())
		{
			for ( Appointment app:appointments)
			{
				list.remove(app);
			}
		}
		
		
	}

	public JComponent getComponent()
	{
		return content;
	}
	
	private Set<Allocatable> getAllAllocatables() throws RaplaException
	{
		Allocatable[] allocatables = getQuery().getAllocatables(calendarModel.getAllocatableFilter());
		Set<Allocatable> rightsToAllocate = new HashSet<Allocatable>();
		Date today = getQuery().today();
		for (Allocatable alloc:allocatables)
		{
			if (alloc.canAllocate(user,today))
			{
				rightsToAllocate.add( alloc );
			}
		}
		return rightsToAllocate;
	}
	
	private Set<Allocatable> getAllocated()
	{
		Allocatable[] allocatables = mutableReservation.getAllocatables();
		Set<Allocatable> result = new HashSet<Allocatable>(Arrays.asList(allocatables));
		return result;
	}
	
	private boolean	bWorkaround	= false;	// Workaround for Bug ID  4480264 on developer.java.sun.com
	
	public void setReservation(Reservation mutableReservation, Reservation originalReservation) throws RaplaException
	{
		this.originalReservation = originalReservation;
		this.mutableReservation = mutableReservation;
		this.user = getUser();
		setAppointments(mutableReservation.getAppointments());
		Collection<Allocatable> allocatableList = getAllAllocatables();
		completeModel.setAllocatables(allocatableList);
		updateBindings( null);
		// Expand allocatableTree if only one DynamicType
		final CalendarModel calendarModel = getService(CalendarModel.class);
		Collection<?> selectedObjectsAndChildren = calendarModel.getSelectedObjects();
		expandObjects(selectedObjectsAndChildren, completeTable.getTree());
		selectedModel.setAllocatables(getAllocated(), selectedTable.getTree());
		expandObjects( getAllocated(), selectedTable.getTree());
		updateButtons();
		JTree tree = selectedTable.getTree();
		for (int i = 0; i < tree.getRowCount(); i++)
			tree.expandRow(i);
		
		// Workaround for Bug ID  4480264 on developer.java.sun.com
		bWorkaround = true;
		if (selectedTable.getRowCount() > 0)
		{
			selectedTable.editCellAt(1, 1);
			selectedTable.editCellAt(1, 0);
		}
		bWorkaround = false;
		//filterAction.removePropertyChangeListener(listener);
//		filterAction.addPropertyChangeListener(listener);
//		btnFilter.setAction(filterAction);
		// We have to add this after processing, because the Adapter in the JTreeTable does the same
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				selectObjects(calendarModel.getSelectedObjects(), completeTable.getTree());
			}
		});
	}
	
	private void setAppointments(Appointment[] appointmentArray)
	{
		List<Appointment> sortedAppointments = new ArrayList<Appointment>(  Arrays.asList( appointmentArray));
		Collections.sort(sortedAppointments, new AppointmentStartComparator() );
		this.appointments = sortedAppointments.toArray( Appointment.EMPTY_ARRAY);
		this.appointmentStrings = new String[appointments.length];
		this.appointmentIndexStrings = new String[appointments.length];
		for (int i = 0; i < appointments.length; i++)
		{
			this.appointmentStrings[i] = getAppointmentFormater().getVeryShortSummary(appointments[i]);
			this.appointmentIndexStrings[i] = getRaplaLocale().formatNumber(i + 1);
		}
	}
	
	private boolean isAllocatableSelected(JTreeTable table)
	{
		// allow folders to be selected
		return isElementSelected(table, false);
	}
	
	private boolean isElementSelected(JTreeTable table, boolean allocatablesOnly)
	{
		int start = table.getSelectionModel().getMinSelectionIndex();
		int end = table.getSelectionModel().getMaxSelectionIndex();
		if (start >= 0)
		{
			for (int i = start; i <= end; i++)
			{
				TreePath path = table.getTree().getPathForRow(i);
				if (path != null && (!allocatablesOnly || ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject() instanceof Allocatable))
					return true;
			}
		}
		return false;
	}
	
	public Set<Allocatable> getMarkedAllocatables()
	{
		return new HashSet<Allocatable>(getSelectedAllocatables(completeTable.getTree()));
	}
	
	protected Collection<Allocatable> getSelectedAllocatables(JTree tree)
	{
		// allow folders to be selected
		Collection<?> selectedElementsIncludingChildren = getSelectedElementsIncludingChildren(tree);
		List<Allocatable> allocatables = new ArrayList<Allocatable>();
		for (Object obj:selectedElementsIncludingChildren)
		{
			if ( obj instanceof Allocatable)
			{
				allocatables.add(( Allocatable) obj);
			}
		}
		return allocatables;
	}
	
	protected Collection<?> getSelectedElementsIncludingChildren(JTree tree)
	{
		TreePath[] paths = tree.getSelectionPaths();
		List<Object> list = new LinkedList<Object>();
        if ( paths == null)
        {
            return list;
        }
        for (TreePath p:paths) 
		{
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) p.getLastPathComponent();
			{
				Object obj = node.getUserObject();
				if (obj != null )
					list.add(obj);
			}
        	Enumeration<?> tt = node.children();
        	for(;tt.hasMoreElements();)
        	{
        		DefaultMutableTreeNode nodeChild = (DefaultMutableTreeNode) tt.nextElement();
        		Object obj = nodeChild.getUserObject();
        		if (obj != null )
        		{	
        			list.add(obj);
        		}
        	}
		}
		return list;
	}
	
	protected void remove(Collection<Allocatable> elements) 
	{
		Iterator<Allocatable> it = elements.iterator();
		boolean bChanged = false;
		while (it.hasNext())
		{
			Allocatable a =  it.next();
			if (mutableReservation.hasAllocated(a))
			{
				mutableReservation.removeAllocatable(a);
				bChanged = true;
			}
		}
		if (bChanged)
		{
			selectedModel.setAllocatables(getAllocated(), selectedTable.getTree());
		}
		fireAllocationsChanged();
	}
	
	protected void add(Collection<Allocatable> elements) 
	{
		Iterator<Allocatable> it = elements.iterator();
		boolean bChanged = false;
		while (it.hasNext())
		{
			Allocatable a =  it.next();
			if (!mutableReservation.hasAllocated(a))
			{
				mutableReservation.addAllocatable(a);
				bChanged = true;
			}
		}
		if (bChanged)
		{
			selectedModel.setAllocatables(getAllocated(), selectedTable.getTree());
			expandObjects(elements, selectedTable.getTree());
		}
		fireAllocationsChanged();
	}
	
	private Date findFirstStart()
	{
		Date firstStart = null;
		for (int i = 0; i < appointments.length; i++)
			if (firstStart == null || appointments[i].getStart().before(firstStart))
				firstStart = appointments[i].getStart();
		return firstStart;
	}
	
	private void updateButtons()
	{
		{
			boolean enable = isElementSelected(completeTable, false);
			calendarAction1.setEnabled(enable);
			enable = enable && isAllocatableSelected(completeTable);
			addAction.setEnabled(enable);
		}
		{
			boolean enable = isElementSelected(selectedTable, false);
			calendarAction2.setEnabled(enable);
			enable = enable && isAllocatableSelected(selectedTable);
			removeAction.setEnabled(enable);
		}
	}
	
	class Listener extends MouseAdapter implements ListSelectionListener, ChangeListener
	{
		public void valueChanged(ListSelectionEvent e)
		{
			updateButtons();
		}
		
		public void mousePressed(MouseEvent me)
		{
			if (me.isPopupTrigger())
				firePopup(me);
		}
		
		public void mouseReleased(MouseEvent me)
		{
			if (me.isPopupTrigger())
				firePopup(me);
		}
		
		public void mouseClicked(MouseEvent evt)
		{
			if (evt.getClickCount() < 2)
				return;
			JTreeTable table = (JTreeTable) evt.getSource();
			int row = table.rowAtPoint(new Point(evt.getX(), evt.getY()));
			if (row < 0)
				return;
			Object obj = table.getValueAt(row, 0);
			if (!(obj instanceof Allocatable))
				return;
			
			AllocatableChange commando;
			if (table == completeTable)
				commando = newAllocatableChange("add",completeTable);
			else
				commando = newAllocatableChange("remove",selectedTable);
			commandHistory.storeAndExecute(commando);

		}
		
		
		public void stateChanged(ChangeEvent e) {
            try {
                ClassifiableFilterEdit filterUI = filter.getFilterUI();
                if ( filterUI != null)
                {
					final ClassificationFilter[] filters = filterUI.getFilters();
	                calendarModel.setAllocatableFilter( filters);
	                completeModel.setAllocatables(getAllAllocatables(), completeTable.getTree());
	                //List<Appointment> appointments = Arrays.asList(mutableReservation.getAppointments());
					// it is important to update all bindings, because a
	                updateBindings( null );
                }
            } catch (Exception ex) {
                showException(ex, getComponent());
            }
        }
	
	}
	
	protected void firePopup(MouseEvent me)
	{
		Point p = new Point(me.getX(), me.getY());
		JTreeTable table = ((JTreeTable) me.getSource());
		int row = table.rowAtPoint(p);
		int column = table.columnAtPoint(p);
		Object selectedObject = null;
		if (row >= 0 && column >= 0)
			selectedObject = table.getValueAt(row, column);
		//System.out.println("row " + row + " column " + column + " selected " + selectedObject);
		showPopup(new PopupEvent(table, selectedObject, p));
	}
	
	public void showPopup(PopupEvent evt)
	{
		try
		{
			Point p = evt.getPoint();
			Object selectedObject = evt.getSelectedObject();
			
			JTreeTable table = ((JTreeTable) evt.getSource());
			RaplaPopupMenu menu = new RaplaPopupMenu();
			if (table == completeTable)
			{
				menu.add(new JMenuItem(addAction));
				menu.add(new JMenuItem(calendarAction1));
			}
			else
			{
				menu.add(new JMenuItem(removeAction));
				menu.add(new JMenuItem(calendarAction2));
			}
			String seperatorId = "ADD_REMOVE_SEPERATOR";
            menu.add( new RaplaSeparator(seperatorId));
			MenuContext menuContext = createMenuContext(p, selectedObject);
			Collection<?> list = getSelectedAllocatables( table.getTree());
			menuContext.setSelectedObjects(list);
			RaplaMenu newMenu = new RaplaMenu("new");
			newMenu.setText(getString("new"));
			MenuFactory menuFactory = getService( MenuFactory.class);
			((MenuFactoryImpl) menuFactory).addNew(newMenu, menuContext, null);

			menuFactory.addObjectMenu(menu, menuContext, seperatorId);
			newMenu.setEnabled( newMenu.getMenuComponentCount() > 0);
			menu.insertAfterId(newMenu, seperatorId);
			menu.show(table, p.x, p.y);
		}
		catch (RaplaException ex)
		{
			showException(ex, getComponent());
		}
	}
	
    protected MenuContext createMenuContext(Point p, Object obj) {
        MenuContext menuContext = new MenuContext(getContext(), obj, getComponent(), p);
        return menuContext;
    }
    
	
	public void expandObjects(Collection<? extends Object> expandedNodes, JTree tree)
    {
		Set<Object> expandedObjects = new LinkedHashSet<Object>();
		expandedObjects.addAll( expandedNodes);
		// we need an enumeration, because we modife the set
        Enumeration<?> enumeration = ((DefaultMutableTreeNode) tree.getModel().getRoot()).preorderEnumeration();
        while (enumeration.hasMoreElements())
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
            Object userObject = node.getUserObject();
			if (expandedObjects.contains(userObject))
            {
                expandedObjects.remove( userObject );
				TreePath path = new TreePath(node.getPath());
                while ( path != null)
                {
                    tree.expandPath(path);
                    path = path.getParentPath();
                }
            }
        }
    }
    
    static public void selectObjects(Collection<?> expandedNodes, JTree tree)
    {
        Enumeration<?> enumeration = ((DefaultMutableTreeNode) tree.getModel().getRoot()).preorderEnumeration();
        List<TreePath> selectionPaths = new ArrayList<TreePath>();
        Set<Object> alreadySelected = new HashSet<Object>();
        while (enumeration.hasMoreElements())
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
            Iterator<?> it = expandedNodes.iterator();
            while (it.hasNext())
            {
                Object userObject = node.getUserObject();
				if (it.next().equals(userObject) && !alreadySelected.contains( userObject))
                {
					alreadySelected.add( userObject );
                    selectionPaths.add(new TreePath(node.getPath()));
                }
            }
        }
        tree.setSelectionPaths( selectionPaths.toArray(new TreePath[] {}));
    }
	class CompleteModel extends AllocatablesModel
	{
		public int getColumnCount()
		{
			return 2;
		}
		
		public boolean isCellEditable(Object node, int column)
		{
			return column > 0;
		}
		
		public Object getValueAt(Object node, int column)
		{
			return ((DefaultMutableTreeNode) node).getUserObject();
		}
		
		public String getColumnName(int column)
		{
			switch (column)
			{
				case 0:
					return getString("selectable");
				case 1:
					return getString("selectable_on");
			}
			throw new IndexOutOfBoundsException();
		}
		
		public Class<?> getColumnClass(int column)
		{
			switch (column)
			{
				case 0:
					return TreeTableModel.class;
				case 1:
					return Allocatable.class;
			}
			throw new IndexOutOfBoundsException();
		}
		
	}
	
	class SelectedModel extends AllocatablesModel
	{
		public SelectedModel() {
			super();
			useCategorizations = false;
		}
		
		public int getColumnCount()
		{
			return 2;
		}
		
		public boolean isCellEditable(Object node, int column)
		{
			if (column == 1 && bWorkaround)
				return true;
			Object o = ((DefaultMutableTreeNode) node).getUserObject();
			if (column == 1 && o instanceof Allocatable)
				return true;
			else
				return false;
		}
		
		public Object getValueAt(Object node, int column)
		{
			Object o = ((DefaultMutableTreeNode) node).getUserObject();
			if (o instanceof Allocatable)
			{
				switch (column)
				{
					case 0:
						return o;
					case 1:
						return mutableReservation.getRestriction((Allocatable) o);
				}
			}
			if (o instanceof DynamicType)
			{
				return o;
			}
			return o;
			//throw new IndexOutOfBoundsException();
		}
		
		public void setValueAt(Object value, Object node, int column)
		{
			Object o = ((DefaultMutableTreeNode) node).getUserObject();
			if (column == 1 && o instanceof Allocatable && value instanceof Appointment[])
			{
				if (mutableReservation.getRestriction((Allocatable) o) != value)
				{
					mutableReservation.setRestriction((Allocatable) o, (Appointment[]) value);
					fireAllocationsChanged();
				}
			}
			fireTreeNodesChanged(node, ((DefaultMutableTreeNode) node).getPath(), new int[] {}, new Object[] {});
		}
		
		public String getColumnName(int column)
		{
			switch (column)
			{
				case 0:
					return getString("selected");
				case 1:
					return getString("selected_on");
			}
			throw new IndexOutOfBoundsException();
		}
		
		public Class<?> getColumnClass(int column)
		{
			switch (column)
			{
				case 0:
					return TreeTableModel.class;
				case 1:
					return Appointment[].class;
			}
			throw new IndexOutOfBoundsException();
		}
	}
	
	abstract class AllocatablesModel extends AbstractTreeTableModel 
	{
		TreeModel	treeModel;
		boolean useCategorizations;
		
		public AllocatablesModel()
		{
			super(new DefaultMutableTreeNode());
			treeModel = new DefaultTreeModel((DefaultMutableTreeNode) super.getRoot());
			useCategorizations = true;
		}
		
		// Types of the columns.
		Collection<Allocatable>	allocatables;
		
		public void setAllocatables(Collection<Allocatable> allocatables) 
		{
			this.allocatables = allocatables;
			
			treeModel = getTreeFactory().createClassifiableModel( allocatables.toArray(Allocatable.ALLOCATABLE_ARRAY), useCategorizations);
			DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
			int childCount = root.getChildCount();
			int[] childIndices = new int[childCount];
			Object[] children = new Object[childCount];
			for (int i = 0; i < childCount; i++)
			{
				childIndices[i] = i;
				children[i] = root.getChildAt(i);
			}
			fireTreeStructureChanged(root, root.getPath(), childIndices, children);
		}
		
		public void setAllocatables(Collection<Allocatable> allocatables, JTree tree) 
		{
			this.allocatables = allocatables;
			Collection<Object> expanded = new HashSet<Object>();
			for (int i = 0; i < tree.getRowCount(); i++)
			{
				if (tree.isExpanded(i))
				{
					DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getPathForRow(i).getLastPathComponent();
					expanded.add(node.getUserObject());
				}
			}
			setAllocatables(allocatables);
			expandNodes(expanded, tree);
		}
		
		void expandNodes(Collection<Object> expanded, JTree tree)
		{
			if (expanded.size() == 0)
				return;
			Collection<Object> expandedToRemove = new LinkedHashSet<Object>( expanded);
			for (int i = 0; i < tree.getRowCount(); i++)
			{
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getPathForRow(i).getLastPathComponent();
				Object userObject = node.getUserObject();
				if (expandedToRemove.contains(userObject))
				{
					expandedToRemove.remove( userObject );
					tree.expandRow(i);
				}
			}
		}
		
		public Collection<Allocatable> getAllocatables()
		{
			return allocatables;
		}
		
		public void treeDidChange()
		{
			DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
			int childCount = root.getChildCount();
			int[] childIndices = new int[childCount];
			Object[] children = new Object[childCount];
			for (int i = 0; i < childCount; i++)
			{
				childIndices[i] = i;
				children[i] = root.getChildAt(i);
			}
			fireTreeNodesChanged(root, root.getPath(), childIndices, children);
		}
		
		public Object getRoot()
		{
			return treeModel.getRoot();
		}
		
		public int getChildCount(Object node)
		{
			return treeModel.getChildCount(node);
		}
		
		public Object getChild(Object node, int i)
		{
			return treeModel.getChild(node, i);
		}
	}
	
	class RestrictionCellRenderer extends DefaultTableCellRenderer
	{
		private static final long	serialVersionUID	= 1L;
		
		Object						newValue;
		JButton						button				= new JButton();
		
		public void setValue(Object value)
		{
			newValue = value;
			super.setValue("");
		}
		
		public void setBounds(int x, int y, int width, int heigth)
		{
			super.setBounds(x, y, width, heigth);
			button.setBounds(x, y, width, heigth);
		}
		
		public void paint(Graphics g)
		{
			Object value = newValue;
			if (value instanceof Appointment[])
			{
				super.paint(g);
				java.awt.Font f = g.getFont();
				button.paint(g);
				g.setFont(f);
				paintRestriction(g, (Appointment[]) value, this);
			}
		}
	}
	
	class AllocationCellRenderer extends DefaultTableCellRenderer
	{
		private static final long	serialVersionUID	= 1L;
		
		Object						newValue;
		
		public void setValue(Object value)
		{
			newValue = value;
			super.setValue("");
		}
		
		public void paint(Graphics g)
		{
			Object value = newValue;
			super.paint(g);
			if (value instanceof Allocatable)
			{
				paintAllocation(g, (Allocatable) value, this);
			}
		}
	}
	
	class RaplaToolTipRenderer implements TableToolTipRenderer
	{
		public String getToolTipText(JTable table, int row, int column)
		{
			Object value = table.getValueAt(row, column);
			return getInfoFactory().getToolTip(value);
		}
	}
	
	private int indexOf(Appointment appointment)
	{
		for (int i = 0; i < appointments.length; i++)
			if (appointments[i].equals(appointment))
				return i;
		return -1;
	}
	
												
	// returns if the user is allowed to allocate the passed allocatable
	private boolean isAllowed(Allocatable allocatable, Appointment appointment)
	{
		Date start = appointment.getStart();
		Date end = appointment.getMaxEnd();
		Date today = getQuery().today();
		return allocatable.canAllocate(user, start, end, today);
	}
	
	class AllocationRendering
	{
		boolean	conflictingAppointments[] = new boolean[appointments.length];	// stores the temp conflicting appointments
		int		conflictCount = 0;				// temp value for conflicts
		int		permissionConflictCount = 0;	// temp value for conflicts that are the result of denied permissions
	}
	
	// calculates the number of conflicting appointments for this allocatable
	private AllocationRendering calcConflictingAppointments(Allocatable allocatable)
	{
		AllocationRendering result = new AllocationRendering();
		final boolean holdBackConflicts = allocatable.isHoldBackConflicts();
		for (int i = 0; i < appointments.length; i++)
		{
			Appointment appointment = appointments[i];
			Collection<Appointment> collection = allocatableBindings.get( allocatable);
			boolean conflictingAppointments = collection != null && collection.contains( appointment);
			result.conflictingAppointments[i] = false;
	            
			if ( conflictingAppointments )
			{
			    if ( ! holdBackConflicts)
			    {
			    	result.conflictingAppointments[i] = true;
			    	result.conflictCount++;
			    }
			}
			else if (!isAllowed(allocatable, appointment) )
			{
                if ( ! holdBackConflicts)
                {
                	result.conflictingAppointments[i] = true;
                	result.conflictCount++;
                }
                result.permissionConflictCount++;
			}
		}
		return result;
	}
	
	private void paintAllocation(Graphics g, Allocatable allocatable, JComponent c)
	{
		AllocationRendering a = calcConflictingAppointments(allocatable);
		if (appointments.length == 0)
		{
		}
		else if (a.conflictCount == 0)
		{
			g.setColor(Color.green);
			g.drawString(getString("every_appointment"), 2, c.getHeight() - 4);
			return;
		} /*
		 * else if (conflictCount == appointments.length) {
		 * g.setColor(Color.red);
		 * g.drawString(getString("zero_appointment"),2,c.getHeight()-4);
		 * return;
		 * }
		 */
		int x = 2;
		Insets insets = c.getInsets();
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < appointments.length; i++)
		{
			if (a.conflictingAppointments[i])
				continue;
			x = paintApp(c, g, fm, i, insets, x);
		}
	}
	
	private void paintRestriction(Graphics g, Appointment[] restriction, JComponent c)
	{
		if (restriction.length == 0)
		{
			g.drawString(getString("every_appointment"), 2, c.getHeight() - 4);
			return;
		}
		int x = 0;
		Insets insets = c.getInsets();
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < appointments.length; i++)
		{
			for (int j = 0; j < restriction.length; j++)
			{
				if (restriction[j].equals(appointments[i]))
					x = paintApp(c, g, fm, i, insets, x);
			}
		}
	}
	
	private int paintApp(Component c, Graphics g, FontMetrics fm, int index, Insets insets, int x)
	{
		int xborder = 4;
		int yborder = 1;
		int width = fm.stringWidth(appointmentIndexStrings[index]);
		x += xborder;
		g.setColor(RaplaColorList.getAppointmentColor(index));
		g.fillRoundRect(x, insets.top, width, c.getHeight() - insets.top - insets.bottom - yborder * 2, 4, 4);
		g.setColor(c.getForeground());
		g.drawRoundRect(x - 1, insets.top, width + 1, c.getHeight() - insets.top - insets.bottom - yborder * 2, 4, 4);
		g.drawString(appointmentIndexStrings[index], x, c.getHeight() - yborder - fm.getDescent());
		x += width;
		x += 2;
		int textWidth = fm.stringWidth(appointmentStrings[index]);
		g.drawString(appointmentStrings[index], x, c.getHeight() - fm.getDescent());
		x += textWidth;
		x += xborder;
		return x;
	}
	
	class RestrictionTextField extends JTextField
	{
		private static final long	serialVersionUID	= 1L;
		
		Object						newValue;
		
		public void setValue(Object value)
		{
			newValue = value;
		}
		
		public void paint(Graphics g)
		{
			Object value = newValue;
			super.paint(g);
			if (value instanceof Appointment[])
			{
				paintRestriction(g, (Appointment[]) value, this);
			}
		}
	}
	
	class AllocationTextField extends JTextField
    {
        private static final long   serialVersionUID    = 1L;
        
        Object                      newValue;
        
        public void setValue(Object value)
        {
            newValue = value;
        }
        
        public void paint(Graphics g)
        {
            Object value = newValue;
            super.paint(g);
            if (value instanceof Allocatable)
            {
                paintAllocation(g, (Allocatable) value, this);
            }
        }
    }

	
	class AppointmentCellEditor extends DefaultCellEditor implements MouseListener, KeyListener, PopupMenuListener, ActionListener
	{
		private static final long	serialVersionUID	= 1L;
		
		JPopupMenu					menu				= new JPopupMenu();
		RestrictionTextField		editingComponent;
		boolean						bStopEditingCalled	= false;			/*
																			 * We need this variable
																			 * to check if
																			 * stopCellEditing
																			 * was already called.
																			 */
		
		DefaultMutableTreeNode		selectedNode;
		int							selectedColumn		= 0;
		Appointment[]				restriction;
		
		public AppointmentCellEditor(RestrictionTextField textField)
		{
			super(textField);
			editingComponent = (RestrictionTextField) this.getComponent();
			editingComponent.setEditable(false);
			
			editingComponent.addMouseListener(this);
			editingComponent.addKeyListener(this);
			menu.addPopupMenuListener(this);
		}
		
		public void mouseReleased(MouseEvent evt)
		{
			showComp();
		}
		
		public void mousePressed(MouseEvent evt)
		{
		}
		
		public void mouseClicked(MouseEvent evt)
		{
		}
		
		public void mouseEntered(MouseEvent evt)
		{
		}
		
		public void mouseExited(MouseEvent evt)
		{
		}
		
		public void keyPressed(KeyEvent evt)
		{
		}
		
		public void keyTyped(KeyEvent evt)
		{
		}
		
		public void keyReleased(KeyEvent evt)
		{
			showComp();
		}
		
		/**
		 * This method is performed, if the user clicks on a menu item of the
		 * <code>JPopupMenu</code> in order to select invividual appointments
		 * for a resource.
		 * 
		 * Changed in Rapla 1.4
		 */
		public void actionPerformed(ActionEvent evt)
		{
			// Refresh the selected appointments for the resource which is being
			// edited
			int oldRestrictionLength = restriction.length;
			Appointment[] oldRestriction = restriction;
			
			Object selectedObject = selectedNode.getUserObject();
			Object source = evt.getSource();
			if ( source == selectedMenu)
			{
				AllocationRendering allocBinding = null;
				if (selectedObject instanceof Allocatable)
				{
					Allocatable allocatable = (Allocatable) selectedObject;
					allocBinding = calcConflictingAppointments(allocatable);
				}
				List<Appointment> newRestrictions = new ArrayList<Appointment>();
				for (int i = 0; i < appointments.length; i++)
				{
					boolean conflicting =  (allocBinding != null && allocBinding.conflictingAppointments[i]);
					( appointmentList.get(i)).setSelected(!conflicting);
					if ( !conflicting)
					{
						newRestrictions.add(appointments[i]);
					}
				}
				restriction = newRestrictions.toArray( Appointment.EMPTY_ARRAY);
				// Refresh the state of the "every Appointment" menu item
				allMenu.setSelected(restriction.length == 0);
				selectedMenu.setSelected(restriction.length != 0);
			}
			else if (source instanceof javax.swing.JCheckBoxMenuItem)
			{
				// Refresh the state of the "every Appointment" menu item
				updateRestriction(Integer.valueOf(evt.getActionCommand()).intValue());
				allMenu.setSelected(restriction.length == 0);
				selectedMenu.setSelected(restriction.length != 0);
			}
			else
			{
				updateRestriction(Integer.valueOf(evt.getActionCommand()).intValue());
				// "every Appointment" has been selected, stop editing
				fireEditingStopped();
				selectedTable.requestFocus();
			}
		

			if (oldRestrictionLength != restriction.length) {
				RestrictionChange commando = new RestrictionChange(	oldRestriction, restriction, selectedNode, selectedColumn);
				commandHistory.storeAndExecute(commando);
			}	

		}
		
		public void popupMenuWillBecomeVisible(PopupMenuEvent e)
		{
			bStopEditingCalled = false;
		}
		
		public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
		{
			if (!bStopEditingCalled)
			{
				AppointmentCellEditor.super.stopCellEditing();
			}
		}
		
		public void popupMenuCanceled(PopupMenuEvent e)
		{
			// BUGID: 4234793
			// This method is never called
		}
		
		Map<Integer,JMenuItem>	appointmentList	= new HashMap<Integer, JMenuItem>();
		JMenuItem	allMenu			= new JRadioButtonMenuItem();
		JMenuItem	selectedMenu	= new JRadioButtonMenuItem();
		
		/**
		 * This method builds and shows the JPopupMenu for the appointment selection
		 * 
		 * Changed in Rapla 1.4
		 */
		private void showComp()
		{
			Object selectedObject = selectedNode.getUserObject();
			AllocationRendering allocBinding = null;
			if (selectedObject instanceof Allocatable)
			{
				Allocatable allocatable = (Allocatable) selectedObject;
				allocBinding = calcConflictingAppointments(allocatable);
			}
			Icon conflictIcon = getI18n().getIcon("icon.allocatable_taken");
			allMenu.setText(getString("every_appointment"));
			selectedMenu.setText(getString("selected_on"));
			appointmentList.clear();
			menu.removeAll();
			allMenu.setActionCommand("-1");
			allMenu.addActionListener(this);
			selectedMenu.setActionCommand("-2");
			selectedMenu.addActionListener( this );
			selectedMenu.setUI(new StayOpenRadioButtonMenuItemUI());
			menu.add(new JMenuItem(getString("close")));
			menu.add(new JSeparator());
			menu.add(allMenu);
			menu.add(selectedMenu);
			
			menu.add(new JSeparator());
			for (int i = 0; i < appointments.length; i++)
			{
				JMenuItem item = new JCheckBoxMenuItem();
				
				// Prevent the JCheckboxMenuItem from closing the JPopupMenu
				item.setUI(new StayOpenCheckBoxMenuItemUI());
				
				// set conflicting icon if appointment causes conflicts
				String appointmentSummary = getAppointmentFormater().getShortSummary(appointments[i]);
				if (allocBinding != null && allocBinding.conflictingAppointments[i])
				{
					item.setText((i + 1) + ": " + appointmentSummary);
					item.setIcon(conflictIcon);
				}
				else
				{
					item.setText((i + 1) + ": " + appointmentSummary);
				}
				appointmentList.put(i, item);
				item.setBackground(RaplaColorList.getAppointmentColor(i));
				item.setActionCommand(String.valueOf(i));
				item.addActionListener(this);
				menu.add(item);
			}
			
			for (int i = 0; i < appointments.length; i++)
			{
				 appointmentList.get(i).setSelected(false);
			}
			
			Appointment[] apps = restriction;
			allMenu.setSelected(apps.length == 0);
			selectedMenu.setSelected(apps.length > 0);
			
			for (int i = 0; i < apps.length; i++)
			{
				//              System.out.println("Select " + indexOf(apps[i]));
			    appointmentList.get(indexOf(apps[i])).setSelected(true);
			}
			
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			Dimension menuSize = menu.getPreferredSize();
			Point location = editingComponent.getLocationOnScreen();
			int diffx = Math.min(0, screenSize.width - (location.x + menuSize.width));
			int diffy = Math.min(0, screenSize.height - (location.y + menuSize.height));
			menu.show(editingComponent, diffx, diffy);
		}
		
		private void setRestriction(Appointment[] restriction)
		{
			this.restriction = restriction;
		}
		
		/** select or deselect the appointment at the given index */
		private void updateRestriction(int index)
		{
			if (index ==  -1)
			{
				restriction = Appointment.EMPTY_ARRAY;
			}
			else if (index ==  -2) 
			{
				restriction = appointments;
			}
			else
			{
				Collection<Appointment> newAppointments = new ArrayList<Appointment>();
				// get the selected appointments
				
				// add all previous selected appointments, except the appointment that
				// is clicked
				for (int i = 0; i < restriction.length; i++)
					if (!restriction[i].equals(appointments[index]))
					{
						newAppointments.add(restriction[i]);
					}
				
				// If the clicked appointment was selected then deselect
				// otherwise select ist
				if (!containsAppointment(appointments[index]))
					newAppointments.add(appointments[index]);
				restriction = newAppointments.toArray(Appointment.EMPTY_ARRAY);
			}
		}
		
		private boolean containsAppointment(Appointment appointment)
		{
			for (int i = 0; i < restriction.length; i++)
				if (restriction[i].equals(appointment))
					return true;
			return false;
		}
		
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
		{
			Component component = super.getTableCellEditorComponent(table, value, isSelected, row, column);
			if (value instanceof Appointment[])
			{
				setRestriction((Appointment[]) value);
				((RestrictionTextField) component).setText("");
			}
			
			((RestrictionTextField) component).setValue(value);
			// Workaround for JDK 1.4 Bug ID: 4234793
			// We have to change the table-model after cell-editing stopped
			this.selectedNode = (DefaultMutableTreeNode) selectedTable.getTree().getPathForRow(row).getLastPathComponent();
			this.selectedColumn = column;
			return component;
		}
		
		public Object getCellEditorValue()
		{
			return restriction;
		}
		
		public boolean shouldSelectCell(EventObject event)
		{
			return true;
		}
		
		public boolean isCellEditable(EventObject event)
		{
			return true;
		}
		
		public boolean stopCellEditing()
		{
			bStopEditingCalled = true;
			boolean bResult = super.stopCellEditing();
			menu.setVisible(false);
			return bResult;
		}
	}
	
	class AppointmentCellEditor2 extends DefaultCellEditor implements MouseListener, KeyListener, PopupMenuListener, ActionListener
    {
        private static final long   serialVersionUID    = 1L;
        
        JPopupMenu                  menu                = new JPopupMenu();
        AllocationTextField        editingComponent;
        boolean                     bStopEditingCalled  = false;            /*
                                                                             * We need this variable
                                                                             * to check if
                                                                             * stopCellEditing
                                                                             * was already called.
                                                                             */
        
        DefaultMutableTreeNode      selectedNode;
        int                         selectedColumn      = 0;
        Appointment[]               restriction;
        
        public AppointmentCellEditor2(AllocationTextField textField)
        {
            super(textField);
            editingComponent = (AllocationTextField) this.getComponent();
            editingComponent.setEditable(false);
            editingComponent.addMouseListener(this);
            editingComponent.addKeyListener(this);
            menu.addPopupMenuListener(this);
        }
        
        public void mouseReleased(MouseEvent evt)
        {
            showComp();
        }
        
        public void mousePressed(MouseEvent evt)
        {
        }
        
        public void mouseClicked(MouseEvent evt)
        {
        }
        
        public void mouseEntered(MouseEvent evt)
        {
        }
        
        public void mouseExited(MouseEvent evt)
        {
        }
        
        public void keyPressed(KeyEvent evt)
        {
        }
        
        public void keyTyped(KeyEvent evt)
        {
        }
        
        public void keyReleased(KeyEvent evt)
        {
            showComp();
        }
        
        /**
         * This method is performed, if the user clicks on a menu item of the
         * <code>JPopupMenu</code> in order to select invividual appointments
         * for a resource.
         * 
         */
        public void actionPerformed(ActionEvent evt)
        {
        }
        
        public void popupMenuWillBecomeVisible(PopupMenuEvent e)
        {
            bStopEditingCalled = false;
        }
        
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
        {
            if (!bStopEditingCalled)
            {
                AppointmentCellEditor2.super.stopCellEditing();
            }
        }
        
        public void popupMenuCanceled(PopupMenuEvent e)
        {
            // BUGID: 4234793
            // This method is never called
        }
        
        
        
        /**
         * This method builds and shows the JPopupMenu for the appointment selection
         * 
         */
        private void showComp()
        {
            Object selectedObject = selectedNode.getUserObject();
            AllocationRendering allocBinding;
            if (selectedObject != null && selectedObject instanceof Allocatable)
            {
                Allocatable allocatable = (Allocatable) selectedObject;
                allocBinding = calcConflictingAppointments(allocatable);
            }
            else
            {
                return;
            }
           
            menu.removeAll();
            boolean test = true;
            for (int i = 0; i < appointments.length; i++)
            {
                if (allocBinding.conflictingAppointments[i])
                    test = false;
            }
            if ( test)
            {
               return;
            }
            else
            {
                for (int i = 0; i < appointments.length; i++)
                {
                    if (allocBinding.conflictingAppointments[i])
                        continue;
    
                    JMenuItem item = new JMenuItem();
                    
                    // Prevent the JCheckboxMenuItem from closing the JPopupMenu
                    
                    // set conflicting icon if appointment causes conflicts
                    String appointmentSummary = getAppointmentFormater().getShortSummary(appointments[i]);
                    if (allocBinding.conflictingAppointments[i])
                    {
                        item.setText((i + 1) + ": " + appointmentSummary);
                        Icon conflictIcon = getI18n().getIcon("icon.allocatable_taken");
                        item.setIcon(conflictIcon);
                    }
                    else
                    {
                        item.setText((i + 1) + ": " + appointmentSummary);
                    }
                    item.setBackground(RaplaColorList.getAppointmentColor(i));
                    menu.add(item);
                }
            }
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Dimension menuSize = menu.getPreferredSize();
            Point location = editingComponent.getLocationOnScreen();
            int diffx = Math.min(0, screenSize.width - (location.x + menuSize.width));
            int diffy = Math.min(0, screenSize.height - (location.y + menuSize.height));
            menu.show(editingComponent, diffx, diffy);
        }
        
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
        {
            Component component = super.getTableCellEditorComponent(table, value, isSelected, row, column);
            if (value instanceof Allocatable)
            {
                ((AllocationTextField) component).setText("");
                
            }
            
            ((AllocationTextField) component).setValue(value);
            // Workaround for JDK 1.4 Bug ID: 4234793
            // We have to change the table-model after cell-editing stopped
            this.selectedNode = (DefaultMutableTreeNode) completeTable.getTree().getPathForRow(row).getLastPathComponent();
            this.selectedColumn = column;
            return component;
        }
        
        public Object getCellEditorValue()
        {
            return restriction;
        }
        
        public boolean shouldSelectCell(EventObject event)
        {
            return true;
        }
        
        public boolean isCellEditable(EventObject event)
        {
            return true;
        }
        
        public boolean stopCellEditing()
        {
            bStopEditingCalled = true;
            boolean bResult = super.stopCellEditing();
            menu.setVisible(false);
            return bResult;
        }
    }
	
	class AllocationTreeCellRenderer extends DefaultTreeCellRenderer
	{
		private static final long	serialVersionUID	= 1L;
		
		Icon						conflictIcon;
		Icon						freeIcon;
		Icon						notAlwaysAvailableIcon;
		Icon						personIcon;
		Icon						personNotAlwaysAvailableIcon;
		Icon						forbiddenIcon;
		boolean						checkRestrictions;
		
		public AllocationTreeCellRenderer(boolean checkRestrictions)
		{
			forbiddenIcon = getI18n().getIcon("icon.no_perm");
			conflictIcon = getI18n().getIcon("icon.allocatable_taken");
			freeIcon = getI18n().getIcon("icon.allocatable_available");
			notAlwaysAvailableIcon = getI18n().getIcon("icon.allocatable_not_always_available");
			personIcon = getI18n().getIcon("icon.tree.persons");
			personNotAlwaysAvailableIcon = getI18n().getIcon("icon.tree.person_not_always_available");
			this.checkRestrictions = checkRestrictions;
			setOpenIcon(getI18n().getIcon("icon.folder"));
			setClosedIcon(getI18n().getIcon("icon.folder"));
			setLeafIcon(freeIcon);
		}
		
		public Icon getAvailableIcon(Allocatable allocatable)
		{
			if (allocatable.isPerson())
				return personIcon;
			else
				return freeIcon;
		}
		
		public Icon getNotAlwaysAvailableIcon(Allocatable allocatable)
		{
			if (allocatable.isPerson())
				return personNotAlwaysAvailableIcon;
			else
				return notAlwaysAvailableIcon;
		}
		
		private Icon getIcon(Allocatable allocatable)
		{
			
			AllocationRendering allocBinding = calcConflictingAppointments(allocatable);
			if (allocBinding.conflictCount == 0)
			{
				return getAvailableIcon(allocatable);
			}
			else if (allocBinding.conflictCount == appointments.length)
			{
				if (allocBinding.conflictCount == allocBinding.permissionConflictCount)
				{
					if (!checkRestrictions)
					{
						return forbiddenIcon;
					}
				}
				else
				{
					return conflictIcon;
				}
			}
			else if (!checkRestrictions)
			{
				return getNotAlwaysAvailableIcon(allocatable);
			}
			for (int i = 0; i < appointments.length; i++)
			{
				Appointment appointment = appointments[i];
				if (mutableReservation.hasAllocated(allocatable, appointment) && !hasPermissionToAllocate(appointment, allocatable))
				{
					return forbiddenIcon;
				}
			}
	
			if (allocBinding.permissionConflictCount - allocBinding.conflictCount == 0)
			{
				return getAvailableIcon(allocatable);
			}
			Appointment[] restriction = mutableReservation.getRestriction(allocatable);
			if (restriction.length == 0)
			{
				return conflictIcon;
			}
			else
			{
				boolean conflict = false;
				for (int i = 0; i < restriction.length; i++)
				{
					Collection<Appointment> list = allocatableBindings.get( allocatable);
					if (list.contains( restriction[i]) )
					{
						conflict = true;
						break;
					}
				}
				if (conflict)
					return conflictIcon;
				else
					return getNotAlwaysAvailableIcon(allocatable);
			}
		}
		
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
			Object nodeInfo = node.getUserObject();
			if (nodeInfo != null && nodeInfo instanceof Named)
			{
				value = ((Named) nodeInfo).getName(getI18n().getLocale());
			}
			
			if (leaf)
			{
				if (nodeInfo instanceof Allocatable)
				{
					setLeafIcon(getIcon((Allocatable) nodeInfo));
				}
			}
			Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			return result;
		}
		
	}
	
	public boolean hasPermissionToAllocate(Appointment appointment,
			Allocatable allocatable) {
		Date today = getQuery().today();
		User workingUser;
		try {
			workingUser = getUser();
		} catch (RaplaException ex) {
			getLogger().error("Can't get permissions!", ex);
			return false;
		}
		if (originalReservation == null) 
		{
			return allocatable.canAllocate(workingUser,	appointment.getStart(), appointment.getMaxEnd(),today);
		}
		else
		{
			return FacadeImpl.hasPermissionToAllocate(workingUser,	appointment, allocatable, originalReservation, today);
		}
	}

	
	class AllocatableAction extends AbstractAction
	{
		private static final long	serialVersionUID	= 1L;
		
		String						command;
		
		AllocatableAction(String command)
		{
			this.command = command;
			if (command.equals("add"))
			{
				putValue(NAME, getString("add"));
				putValue(SMALL_ICON, getIcon("icon.arrow_right"));
				
			}
			if (command.equals("remove"))
			{
				putValue(NAME, getString("remove"));
				putValue(SMALL_ICON, getIcon("icon.arrow_left"));
			}
			if (command.equals("calendar1") || command.equals("calendar2"))
			{
				putValue(NAME, getString("calendar"));
				putValue(SMALL_ICON, getIcon("icon.calendar"));
			}
		}
		
		public void actionPerformed(ActionEvent evt)
		{
			if (command.equals("add")) {
				AllocatableChange commando = newAllocatableChange(command,completeTable);
				commandHistory.storeAndExecute(commando);
			}
			if (command.equals("remove")) {
				AllocatableChange commando = newAllocatableChange(command,selectedTable);
				commandHistory.storeAndExecute(commando);
			}
			if (command.indexOf("calendar") >= 0)
			{
				JTreeTable tree = (command.equals("calendar1") ? completeTable : selectedTable);
				CalendarAction calendarAction = new CalendarAction(getContext(), getComponent(), calendarModel);
				calendarAction.changeObjects(new ArrayList<Object>(getSelectedAllocatables(tree.getTree())));
				calendarAction.setStart(findFirstStart());
				calendarAction.actionPerformed(evt);
			}
		}

	}


/**
 * This class is used to prevent the JPopupMenu from disappearing when a
 * <code>JCheckboxMenuItem</code> is clicked.
 * 
 * @since Rapla 1.4
 * @see http://forums.oracle.com/forums/thread.jspa?messageID=5724401#5724401
 */
	class StayOpenCheckBoxMenuItemUI extends BasicCheckBoxMenuItemUI
	{
		protected void doClick(MenuSelectionManager msm)
		{
			menuItem.doClick(0);
		}
	}
	
	class StayOpenRadioButtonMenuItemUI extends BasicRadioButtonMenuItemUI
	{
		protected void doClick(MenuSelectionManager msm)
		{
			menuItem.doClick(0);
		}
	}
	 
	private AllocatableChange newAllocatableChange(String command,JTreeTable treeTable) 
	{
		Collection<Allocatable> elements = getSelectedAllocatables( treeTable.getTree());
		return new AllocatableChange(command, elements);
	}

	/**
	 * This Class collects any information changes done to selected or deselected allocatables.
	 * This is where undo/redo for the Allocatable-selection at the bottom of the edit view
	 * is realized. 
	 * @author Jens Fritz
	 *
	 */
	
    //Erstellt und bearbeitet von Matthias Both und Jens Fritz
	public class AllocatableChange implements CommandUndo<RuntimeException> {
		String command;
		Collection<Allocatable> elements;

		public AllocatableChange(String command,
				Collection<Allocatable> elements) {
			this.command = command;

			List<Allocatable> changed = new ArrayList<Allocatable>();
			boolean addOrRemove;
			if (command.equals("add"))
				addOrRemove = false;
			else
				addOrRemove = true;

			Iterator<Allocatable> it = elements.iterator();
			while (it.hasNext()) {
				Allocatable a = it.next();
				if (mutableReservation.hasAllocated(a) == addOrRemove) {
					changed.add(a);
				}
			}

			this.elements = changed;
		}

		public boolean execute()  {
			if (command.equals("add"))
				add(elements);
			else
				remove(elements);
			return true;
		}

		public boolean undo() {
			if (command.equals("add"))
				remove(elements);
			else
				add(elements);
			return true;
		}
		
		public String getCommandoName() 
		{
			return getString(command) + " " + getString("resource");
		}

	}
 
 /**
	 * This Class collects any information of changes done to the exceptions
	 * of an selected allocatable.
	 * This is where undo/redo for the Allocatable-exceptions at the bottom of the edit view
	 * is realized.
	 * @author Jens Fritz
	 *
	 */
    
    //Erstellt von Matthias Both
	public class RestrictionChange implements CommandUndo<RuntimeException> {
		Appointment[] oldRestriction;
		Appointment[] newRestriction;
		DefaultMutableTreeNode selectedNode;
		int selectedColumn;

		public RestrictionChange(Appointment[] old, Appointment[] newOne,
				DefaultMutableTreeNode selectedNode, int selectedColummn) {
			this.oldRestriction = old;
			this.newRestriction = newOne;
			this.selectedNode = selectedNode;
			this.selectedColumn = selectedColummn;
		}

		public boolean execute() {
			selectedModel.setValueAt(newRestriction, selectedNode,
					selectedColumn);
			return true;
		}

		public boolean undo() {
			selectedModel.setValueAt(oldRestriction, selectedNode,
					selectedColumn);
			return true;
		}
		
		public String getCommandoName() 
		{
			return getString("change") + " " + getString("constraints");
		}

	}
	
}