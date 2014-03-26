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
package org.rapla.plugin.periodcopy;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.swing.JMenuItem;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ReservationStartComparator;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.SaveUndo;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.gui.toolkit.RaplaMenuItem;

public class CopyPluginMenu  extends RaplaGUIComponent implements IdentifiableMenuEntry,ActionListener
{
	RaplaMenuItem item;
	String id = "copy_events";
	final String label ;
    public CopyPluginMenu(RaplaContext sm)  {
        super(sm);
        setChildBundleName( PeriodCopyPlugin.RESOURCE_FILE);
        //menu.insert( new RaplaSeparator("info_end"));

        label =getString(id) ;
		item = new RaplaMenuItem(id);

//      ResourceBundle bundle = ResourceBundle.getBundle( "org.rapla.plugin.periodcopy.PeriodCopy");
       
        //bundle.getString("copy_events");
        item.setText( label );
        item.setIcon( getIcon("icon.copy") );
        item.addActionListener(this);
    }

    
	public String getId() {
		return id;
	}


	public JMenuItem getMenuElement() {
		return item;
	}
    
  
 
//    public void copy(CalendarModel model, Period sourcePeriod, Period destPeriod,boolean includeSingleAppointments) throws RaplaException {
//    	Reservation[] reservations = model.getReservations( sourcePeriod.getStart(), sourcePeriod.getEnd() );
//        copy( reservations, destPeriod.getStart(), destPeriod.getEnd(),includeSingleAppointments);
//    }
    
    public void actionPerformed(ActionEvent evt) {
        try {
            final CopyDialog useCase = new CopyDialog(getContext());
            String[] buttons = new String[]{getString("abort"), getString("copy") };
            final DialogUI dialog = DialogUI.create( getContext(),getMainComponent(),true, useCase.getComponent(), buttons);
            dialog.setTitle( label);
            dialog.setSize( 600, 500);
            dialog.getButton( 0).setIcon( getIcon("icon.abort"));
            dialog.getButton( 1).setIcon( getIcon("icon.copy"));
            
//            ActionListener listener = new ActionListener() {
//                public void actionPerformed(ActionEvent arg0) {
//                    dialog.getButton( 1).setEnabled( useCase.isSourceDifferentFromDest() );
//                }
//            };
//          
            dialog.startNoPack();
            final boolean includeSingleAppointments = useCase.isSingleAppointments();
            
            if ( dialog.getSelectedIndex() == 1) {
            	
            	List<Reservation> reservations = useCase.getReservations();
            	copy( reservations, useCase.getDestStart(), useCase.getDestEnd(), includeSingleAppointments );
            }
         } catch (Exception ex) {
            showException( ex, getMainComponent() );
        }
    }
    
    public void copy(  List<Reservation> reservations , Date destStart, Date destEnd,boolean includeSingleAppointmentsAndExceptions) throws RaplaException {
        List<Reservation> newReservations = new ArrayList<Reservation>();
        List<Reservation> sortedReservations = new ArrayList<Reservation>( reservations);
        Collections.sort( sortedReservations, new ReservationStartComparator(getLocale()));
        Date firstStart = null;
        for (Reservation reservation: sortedReservations) {
            if ( firstStart == null )
            {
            	firstStart = ReservationStartComparator.getStart( reservation);
            }
            Reservation r = copy(reservation, destStart,
					destEnd, includeSingleAppointmentsAndExceptions,
					firstStart);
            if ( r.getAppointments().length > 0) {
                newReservations.add( r );
            }

        }
        Collection<Reservation> originalEntity = null;
		SaveUndo<Reservation> cmd = new SaveUndo<Reservation>(getContext(), newReservations, originalEntity);
        getModification().getCommandHistory().storeAndExecute( cmd);
    }

	public Reservation copy(Reservation reservation, Date destStart,
			Date destEnd, boolean includeSingleAppointmentsAndExceptions,
			Date firstStart) throws RaplaException {
		Reservation r = getModification().clone( reservation);
		if ( firstStart == null )
		{
			firstStart = ReservationStartComparator.getStart( reservation);
		}
        
		Appointment[] appointments = r.getAppointments();
	
		for ( Appointment app :appointments) {
			Repeating repeating = app.getRepeating();
		    if (( repeating == null && !includeSingleAppointmentsAndExceptions) || (repeating != null && repeating.getEnd() == null)) {
		        r.removeAppointment( app );
		        continue;
		    }
		    
		    Date oldStart = app.getStart();
		    // we need to calculate an offset so that the reservations will place themself relativ to the first reservation in the list
		    long offset = DateTools.countDays( firstStart, oldStart) * DateTools.MILLISECONDS_PER_DAY;
		    Date newStart ;
		    Date destWithOffset = new Date(destStart.getTime() + offset );
		    if ( repeating != null && repeating.getType().equals ( Repeating.DAILY) ) 
		    {
				newStart = getRaplaLocale().toDate(  destWithOffset  , oldStart );
		    } 
		    else 
		    {
		        newStart = getNewStartWeekly(oldStart, destWithOffset);
		    }
		    app.move( newStart) ;
		    if (repeating != null)
		    {
		    	Date[] exceptions = repeating.getExceptions();
		    	if ( includeSingleAppointmentsAndExceptions )
		    	{
		    		repeating.clearExceptions();
		       		for (Date exc: exceptions)
		    		{
		    		 	long days = DateTools.countDays(oldStart, exc);
		        		Date newDate = DateTools.addDays(newStart, days);
		        		repeating.addException( newDate);
		    		}
		    	}
		    	
		    	if ( !repeating.isFixedNumber())
		    	{
		        	Date oldEnd = repeating.getEnd();
		        	if ( oldEnd != null)
		        	{
		            	if (destEnd != null)
		            	{
		            		repeating.setEnd( destEnd);
		            	}
		            	else 
		            	{
		            		// If we don't have and endig destination, just make the repeating to the original length
		                	long days = DateTools.countDays(oldStart, oldEnd);
		            		Date end = DateTools.addDays(newStart, days);
		            		repeating.setEnd( end);
		            	}
		        	}
		    	}	    
		    }
		        //                System.out.println(reservations[i].getName( getRaplaLocale().getLocale()));
		}
		return r;
	}

	private Date getNewStartWeekly(Date oldStart, Date destStart) {
		Date newStart;
		Calendar calendar = getRaplaLocale().createCalendar();
		calendar.setTime( oldStart);
		int weekday = calendar.get(Calendar.DAY_OF_WEEK);
		calendar = getRaplaLocale().createCalendar();
		calendar.setTime(destStart);
		calendar.set( Calendar.DAY_OF_WEEK, weekday);
		if ( calendar.getTime().before( destStart)) 
		{
		    calendar.add( Calendar.DATE, 7);
		}
		Date firstOccOfWeekday  = calendar.getTime();
		newStart = getRaplaLocale().toDate(  firstOccOfWeekday, oldStart );
		return newStart;
	}




	


}

