/*--------------------------------------------------------------------------* | Copyright (C) 2008  Christopher Kohlhaas                                 |
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
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.Conflict;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.internal.common.MultiCalendarView;
import org.rapla.gui.internal.view.TreeFactoryImpl;
import org.rapla.gui.toolkit.RaplaTree;
import org.rapla.gui.toolkit.RaplaWidget;


public class ConflictSelection extends RaplaGUIComponent implements RaplaWidget {
	public RaplaTree treeSelection = new RaplaTree();
	protected final CalendarSelectionModel model;
	MultiCalendarView view;
	protected JPanel content = new JPanel();
	JLabel summary = new JLabel();
	Collection<Conflict> conflicts;
	 
    public ConflictSelection(RaplaContext context,final MultiCalendarView view, final CalendarSelectionModel model) throws RaplaException {
        super(context);
        this.model = model;
        this.view = view;
        try
        {
        	conflicts = new LinkedHashSet<Conflict>( Arrays.asList(getQuery().getConflicts( )));
        }
        catch ( RaplaException ex)
        {
        	showException( ex, null);
        	this.conflicts = new LinkedHashSet<Conflict>();
        }
        updateTree();
        final JTree navTree = treeSelection.getTree();
        content.setLayout(new BorderLayout());

        content.add(treeSelection);
        // content.setPreferredSize(new Dimension(260,400));
        content.setBorder(BorderFactory.createRaisedBevelBorder());
        JTree tree = treeSelection.getTree();
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(((TreeFactoryImpl) getTreeFactory()).createConflictRenderer());
        tree.setSelectionModel(((TreeFactoryImpl) getTreeFactory()).createConflictTreeSelectionModel());

        navTree.addTreeSelectionListener(new TreeSelectionListener() {

            public void valueChanged(TreeSelectionEvent e) 
            {
            	Collection<Conflict> selectedConflicts = getSelectedConflicts();
            	showConflicts(selectedConflicts);
            }
        });
    }

    public Date getStartDate(Conflict conflict) {
        Date today = getQuery().today();
		Date fromDate = today;
        Date start1 = conflict.getAppointment1().getStart();
		if ( start1.before( fromDate))
        {
        	fromDate = start1; 
        }
        Date start2 = conflict.getAppointment2().getStart();
		if ( start2.before( fromDate))
        {
        	fromDate = start2; 
        }
        Date toDate = DateTools.addDays( today, 365 * 10);
        Date date = conflict.getFirstConflictDate(fromDate, toDate);
        return date;
    }
    
    public RaplaTree getTreeSelection() {
        return treeSelection;
    }

    protected CalendarSelectionModel getModel() {
        return model;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException {
    	TimeInterval invalidateInterval = evt.getInvalidateInterval();
    	if ( invalidateInterval != null && invalidateInterval.getStart() == null)
    	{
    		 Conflict[] conflictArray = getQuery().getConflicts( );
    		 conflicts = new LinkedHashSet<Conflict>( Arrays.asList(conflictArray));
    		 updateTree();
    	}
    	else if ( evt.isModified(Conflict.TYPE) || (evt.isModified( Preferences.TYPE) ) )
        {
        	Set<Conflict> changed = evt.getChanged(conflicts);
        	removeAll( conflicts,changed);
        	
     		Set<Conflict> removed = evt.getRemoved( conflicts);
        	removeAll( conflicts,removed);
        	
        	conflicts.addAll( changed);
        	for (RaplaObject obj:evt.getAddObjects())
        	{
        		if ( obj.getRaplaType()== Conflict.TYPE)
        		{
        			Conflict conflict = (Conflict) obj;
        			conflicts.add( conflict );
        		}
        	}
        	updateTree();
        }
        else
        {
        	treeSelection.repaint();
        }
    }
    
    private void removeAll(Collection<Conflict> list,
			Set<Conflict> changed) {
    	Iterator<Conflict> it = list.iterator();
    	while ( it.hasNext())
    	{
    		if ( changed.contains(it.next()))
    		{
    			it.remove();
    		}
    	}
		
	}

	public JComponent getComponent() {
        return content;
    }
    
    final protected TreeFactory getTreeFactory() {
        return  getService(TreeFactory.class);
    }
    
    private void showConflicts(Collection<Conflict> selectedConflicts) {
         ArrayList<RaplaObject> arrayList = new ArrayList<RaplaObject>(model.getSelectedObjects());
         for ( Iterator<RaplaObject> it = arrayList.iterator();it.hasNext();)
         {
             RaplaObject obj = it.next();
             if (obj.getRaplaType() == Conflict.TYPE )
             {
                 it.remove();
             }
         }
         arrayList.addAll( selectedConflicts);
         model.setSelectedObjects( arrayList);
         if (  !selectedConflicts.isEmpty() )
         {
             Conflict conflict = selectedConflicts.iterator().next();
             Date date = getStartDate(conflict);
             if ( date != null)
             {
                 model.setSelectedDate(date);
             }
         }
         try {
            view.getSelectedCalendar().update();
        } catch (RaplaException e1) {
            getLogger().error("Can't switch to conflict dates.", e1);
        }
    }

    private Collection<Conflict> getSelectedConflictsInModel() {
        Set<Conflict> result = new LinkedHashSet<Conflict>();
        for (RaplaObject obj:model.getSelectedObjects())
        {
            if (obj.getRaplaType() == Conflict.TYPE )
            {
                result.add( (Conflict) obj);
            }
        }
        return result;
    }
    
    private Collection<Conflict> getSelectedConflicts()  {
        List<Object> lastSelected = treeSelection.getSelectedElements( true);
        Set<Conflict> selectedConflicts = new LinkedHashSet<Conflict>();
        for ( Object selected:lastSelected)
        {
             if (selected instanceof Conflict)
             {
                 selectedConflicts.add((Conflict)selected );
             }
        }
        return selectedConflicts;
    }


    private void updateTree() throws RaplaException {
      
        Collection<Conflict> conflicts = getConflicts();
		TreeModel treeModel =  getTreeFactory().createConflictModel(conflicts);
        try {
            treeSelection.exchangeTreeModel(treeModel);
            treeSelection.getTree().expandRow(0);
        } finally {
        }
        summary.setText( getString("conflicts") + " (" + conflicts.size() + ") ");
        Collection<Conflict> selectedConflicts = new ArrayList<Conflict>(getSelectedConflicts());
        Collection<Conflict> inModel = new ArrayList<Conflict>(getSelectedConflictsInModel());
        if ( !selectedConflicts.equals( inModel ))
        {
            showConflicts(selectedConflicts);
        }
    }
    
    public Collection<Conflict> getConflicts() throws RaplaException {
        Collection<Conflict> conflicts;
        boolean onlyOwn = model.isOnlyCurrentUserSelected();
        User conflictUser = onlyOwn ? getUser() : null;
        conflicts= getConflicts( conflictUser);
        return conflicts;
    }
    
    private Collection<Conflict> getConflicts( User user) {

        List<Conflict> result = new ArrayList<Conflict>();
        for (Conflict conflict:conflicts) {
            User owner1 = conflict.getReservation1().getOwner();
			User owner2 = conflict.getReservation2().getOwner();
			if (user != null && !user.equals(owner1) && !user.equals(owner2)) {
                continue;
            }
            result.add(conflict);
        }
        Collections.sort( result, new ConflictStartDateComparator( ));
        return result;
    }
    
    class ConflictStartDateComparator implements Comparator<Conflict>
    {
        public int compare(Conflict c1, Conflict c2) {
           if ( c1.equals( c2))
           {
               return 0;
           }
           Date d1 = getStartDate( c1);
           Date d2 = getStartDate( c2);
           if ( d1 != null )
           {
               if ( d2 == null)
               {
                   return -1;
               }
               else
               {
                   int result = d1.compareTo( d2);
                   return result;
               }
           } 
           else if ( d2 !=  null)
           {
               return 1;
           }
           return new Integer(c1.hashCode()).compareTo( new Integer(c2.hashCode()));
        }
        
    }

    public void clearSelection() 
    {
        treeSelection.getTree().setSelectionPaths( new TreePath[] {});
    }

    public Component getSummaryComponent() {
        return summary;
    }   
    
   
}
