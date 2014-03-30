
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

package org.rapla.plugin.dayresource;

import java.awt.Point;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.CalendarView;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SelectionHandler.SelectionStrategy;
import org.rapla.components.calendarview.swing.SwingWeekView;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.abstractcalendar.AbstractRaplaBlock;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.plugin.weekview.SwingDayCalendar;


public class SwingDayResourceCalendar extends SwingDayCalendar
{
    public SwingDayResourceCalendar( RaplaContext sm, CalendarModel model, boolean editable ) throws RaplaException
    {
        super( sm, model, editable );
    }
    
  
   
    protected AbstractSwingCalendar createView(boolean showScrollPane) {
        /** renderer for dates in weekview */
        SwingWeekView wv = new SwingWeekView( showScrollPane ) {
        	{
        		selectionHandler.setSelectionStrategy(SelectionStrategy.BLOCK);
        	}
            @Override
            protected JComponent createSlotHeader(Integer column) {
                JLabel component = (JLabel) super.createSlotHeader(column);
                try {
                	List<Allocatable> sortedAllocatables = getSortedAllocatables();
                	Allocatable allocatable = sortedAllocatables.get(column);
                	String name = allocatable.getName( getLocale());
					component.setText( name);
					component.setToolTipText(name);
				} catch (RaplaException e) {
				}
                return component;
            }
            
            @Override
            protected int getColumnCount() {
            	try {
        		  Allocatable[] selectedAllocatables =model.getSelectedAllocatables();
        		  return selectedAllocatables.length;
          	  	} catch (RaplaException e) {
          	  		return 0;
          	  	}
            }
            
            @Override
            public void rebuild() {
                super.rebuild();
                String dateText = getRaplaLocale().formatDateShort(getStartDate());
                weekTitle.setText( dateText);
            }
        };
        return wv;
    }
    
    protected RaplaBuilder createBuilder() throws RaplaException
    {
    	RaplaBuilder builder = super.createBuilder();
        builder.setSplitByAllocatables( true );
        
        final List<Allocatable> allocatables = getSortedAllocatables();
        builder.selectAllocatables(allocatables);
        GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy( getRaplaLocale().getLocale() )
        {
        	@Override
        	protected Map<Block, Integer> getBlockMap(CalendarView wv,
        			List<Block> blocks) 
        	{
        		if (allocatables != null)
        		{
        			Map<Block,Integer> map = new LinkedHashMap<Block, Integer>(); 
        			for (Block block:blocks)
        			{
        				int index = getIndex(allocatables, block);
        				
        				if ( index >= 0 )
        				{
        					map.put( block, index );
        				}
        		     }
        		     return map;		
        		}
        		else 
        		{
        			return super.getBlockMap(wv, blocks);
        		}
        	}

			
        };
       
        strategy.setResolveConflictsEnabled( true );
        builder.setBuildStrategy( strategy );
        return builder;
    }
    
  
    protected ViewListener createListener() throws RaplaException {
    	return  new RaplaCalendarViewListener(getContext(), model, view.getComponent()) {
            
            @Override
            protected Collection<Allocatable> getMarkedAllocatables()
            {
        		final List<Allocatable> selectedAllocatables = getSortedAllocatables();
        		 
        		Set<Allocatable> allSelected = new HashSet<Allocatable>();
        		if ( selectedAllocatables.size() == 1 ) {
        			allSelected.add(selectedAllocatables.get(0));
        		}
        		   
        		for ( int i =0 ;i< selectedAllocatables.size();i++)
        		{
        			if ( view.isSelected(i))
        			{
        				allSelected.add(selectedAllocatables.get(i));
        			}
        		}
        		return allSelected;
        	}
		
			 @Override
			 public void moved(Block block, Point p, Date newStart, int slotNr) {
				 int column= slotNr;//getIndex( selectedAllocatables, block );
				 if ( column < 0)
				 {
					 return;
				 }
				 
				 try 
				 {
					 final List<Allocatable> selectedAllocatables = getSortedAllocatables();
					 Allocatable newAlloc = selectedAllocatables.get(column);
					 AbstractRaplaBlock raplaBlock = (AbstractRaplaBlock)block;
					 Allocatable oldAlloc = raplaBlock.getGroupAllocatable();
					 if ( newAlloc != null && oldAlloc != null && !newAlloc.equals(oldAlloc))
					 {
						 AppointmentBlock appointmentBlock= raplaBlock.getAppointmentBlock();
						 getReservationController().exchangeAllocatable(appointmentBlock, oldAlloc,newAlloc,newStart, getMainComponent(),p);
					 }
					 else
					 {
						 super.moved(block, p, newStart, slotNr);
					 }
					 
				 } 
				 catch (RaplaException ex) {
					showException(ex, getMainComponent());
				}
			
			 }
			
            

        };
    }
    
    private int getIndex(final List<Allocatable> allocatables,
			Block block) {
		AbstractRaplaBlock b = (AbstractRaplaBlock)block;
		Allocatable a = b.getGroupAllocatable();
		int index = a != null ? allocatables.indexOf( a ) : -1;
		return index;
	}
}
