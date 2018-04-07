
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.plugin.dayresource.client.swing;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.menu.MenuFactory;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.BlockContainer;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.SelectionHandler.SelectionStrategy;
import org.rapla.components.calendarview.swing.SwingWeekView;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.RaplaBlock;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.plugin.weekview.client.swing.SwingDayCalendar;
import org.rapla.scheduler.Promise;

import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Point;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SwingDayResourceCalendar extends SwingDayCalendar
{
    public SwingDayResourceCalendar(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model, boolean editable,
            boolean printing, final Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, Provider<DateRenderer> dateRendererProvider,
            CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, ReservationController reservationController, InfoFactory infoFactory,
            DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory,
            IOInterface ioInterface, AppointmentFormater appointmentFormater, EditController editController) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger, model, editable, printing, objectMenuFactories, menuFactory, dateRendererProvider, calendarSelectionModel, clipboard, reservationController,
                infoFactory,  dateRenderer, dialogUiFactory, ioInterface, appointmentFormater, editController);
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
					Collection<Allocatable> selectedAllocatables =model.getSelectedAllocatablesAsList();
					return selectedAllocatables.size();
          	  	} catch (RaplaException e) {
          	  		return 0;
          	  	}
            }
            
            @Override
            public void rebuild(Builder b) {
                super.rebuild(b);
                String dateText = getRaplaLocale().formatDateShort(getStartDate());
                weekTitle.setText( dateText);
            }
        };
        return wv;
    }
    
    protected Promise<RaplaBuilder> createBuilder() 
    {
    	Promise<RaplaBuilder> builderPromise = super.createBuilder();
        final Promise<RaplaBuilder> nextBuilderPromise = builderPromise.thenApply((builder) ->
        {
            builder.setSplitByAllocatables(true);
            return builder;
        });
        return nextBuilderPromise;
    }

    @NotNull
    @Override
    protected GroupAllocatablesStrategy createStrategy(RaplaBuilder builder) throws RaplaException
    {
        final List<Allocatable> allocatables = getSortedAllocatables();
        GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy(getRaplaLocale().getLocale())
        {
            @Override
            protected Map<Block, Integer> getBlockMap(BlockContainer wv, List<Block> blocks, Date startDate)
            {
                if (allocatables != null)
                {
                    Map<Block, Integer> map = new LinkedHashMap<>();
                    for (Block block : blocks)
                    {
                        int index = getIndex(allocatables, block);

                        if (index >= 0)
                        {
                            map.put(block, index);
                        }
                    }
                    return map;
                }
                else
                {
                    return super.getBlockMap(wv, blocks, startDate);
                }
            }
        };
        strategy.setResolveConflictsEnabled(true);
        strategy.setOffsetMinutes( view.getOffsetMinutes());
        return strategy;
    }

    protected ViewListener createListener() {
    	return  new RaplaCalendarViewListener(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), model, view.getComponent(),  menuFactory,  reservationController,  dialogUiFactory, editController) {
            
            @Override
            protected Collection<Allocatable> getMarkedAllocatables()
            {
        		final List<Allocatable> selectedAllocatables = getSortedAllocatables();
        		 
        		Set<Allocatable> allSelected = new HashSet<>();
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
                 final List<Allocatable> selectedAllocatables = getSortedAllocatables();
                 Allocatable newAlloc = selectedAllocatables.get(column);
                 RaplaBlock raplaBlock = (RaplaBlock)block;
                 Allocatable oldAlloc = raplaBlock.getGroupAllocatable();
                 Promise<Void> ready;
                 if ( newAlloc != null && oldAlloc != null && !newAlloc.equals(oldAlloc))
                 {
                     AppointmentBlock appointmentBlock= raplaBlock.getAppointmentBlock();
                     PopupContext popupContext = createPopupContext(getMainComponent(),p);
                     ready = reservationController.exchangeAllocatable(appointmentBlock, oldAlloc,newAlloc,newStart, popupContext);
                 }
                 else
                 {
                     ready = super.moved(block, p, newStart);
                 }
                 handleException( ready);
			 }
			
            

        };
    }
    
    private int getIndex(final List<Allocatable> allocatables,
			Block block) {
		RaplaBlock b = (RaplaBlock)block;
		Allocatable a = b.getGroupAllocatable();
		int index = a != null ? allocatables.indexOf( a ) : -1;
		return index;
	}
}
