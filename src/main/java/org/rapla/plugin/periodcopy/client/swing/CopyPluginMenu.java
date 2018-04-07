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
package org.rapla.plugin.periodcopy.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EditMenuExtension;
import org.rapla.client.internal.SaveUndo;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ReservationStartComparator;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.periodcopy.PeriodCopyResources;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Extension(provides = EditMenuExtension.class,id="org.rapla.plugin.periodcopy")
public class CopyPluginMenu  extends RaplaGUIComponent implements EditMenuExtension, ActionListener
{
	RaplaMenuItem item;
	String id = "copy_events";
	final String label ;
    private final PeriodCopyResources periodCopyI18n;
    private final Provider<CopyDialog> copyDialogProvider;
    private final DialogUiFactoryInterface dialogUiFactory;
	@Inject
    public CopyPluginMenu(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, PeriodCopyResources periodCopyI18n, Provider<CopyDialog> copyDialogProvider,  DialogUiFactoryInterface dialogUiFactory)  {
        super(facade, i18n, raplaLocale, logger);
        //menu.insert( new RaplaSeparator("info_end"));
        this.periodCopyI18n = periodCopyI18n;
        this.copyDialogProvider = copyDialogProvider;
        this.dialogUiFactory = dialogUiFactory;

        label =periodCopyI18n.getString(id) ;
		item = new RaplaMenuItem(id);

//      ResourceBundle bundle = ResourceBundle.getBundle( "org.rapla.plugin.periodcopy.PeriodCopy");
       
        item.setText( label );
        item.setIcon( RaplaImages.getIcon(i18n.getIcon("icon.copy") ));
        item.addActionListener(this);
    }


    @Override
	public String getId() {
		return id;
	}


	@Override
	public JMenuItem getComponent() {
		return item;
	}
    
  
 
//    public void copyReservations(CalendarModel model, Period sourcePeriod, Period destPeriod,boolean includeSingleAppointments) throws RaplaException {
//    	Reservation[] reservations = model.getReservations( sourcePeriod.getStart(), sourcePeriod.getEnd() );
//        copyReservations( reservations, destPeriod.getStart(), destPeriod.getEnd(),includeSingleAppointments);
//    }
    
    public void actionPerformed(ActionEvent evt) {
		PopupContext popupContext = dialogUiFactory.createPopupContext( null);

            final CopyDialog useCase = copyDialogProvider.get();
            String[] buttons = new String[]{getString("abort"), getString("copy") };
		final JComponent component = useCase.getComponent();
		component.setSize( 600, 500);
		final DialogInterface dialog = dialogUiFactory.createContentDialog( popupContext, component, buttons);
            dialog.setTitle( label);
            dialog.getAction( 0).setIcon( i18n.getIcon("icon.abort"));
            dialog.getAction( 1).setIcon( i18n.getIcon("icon.copy"));
            
//            ActionListener listener = new ActionListener() {
//                public void actionPerformed(ActionEvent arg0) {
//                    dialog.getButton( 1).setEnabled( useCase.isSourceDifferentFromDest() );
//                }
//            };
            dialog.start(false).thenCompose( index->
			{
				final boolean includeSingleAppointments = useCase.isSingleAppointments();
				if (index == 1) {

					Promise<List<Reservation>> reservationsPromise = useCase.getReservations();
					return reservationsPromise.thenAccept((reservations) ->
						copy(reservations, useCase.getDestStart(), useCase.getDestEnd(), includeSingleAppointments)
					);
				}
				else
				{
					return ResolvedPromise.VOID_PROMISE;
				}
			}).exceptionally( ex ->dialogUiFactory.showException( ex, popupContext ));
    }
    
    public void copy(  Collection<Reservation> reservations , Date destStart, Date destEnd,boolean includeSingleAppointmentsAndExceptions) throws RaplaException {
        Map<Reservation,Reservation> newReservations = new LinkedHashMap<>();
        List<Reservation> sortedReservations = new ArrayList<>(reservations);
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
                newReservations.put( r, r );
            }
        }
		SaveUndo<Reservation> cmd = new SaveUndo<>(getFacade(), getI18n(), newReservations);
        getClientFacade().getCommandHistory().storeAndExecute( cmd);
    }

	public Reservation copy(Reservation reservation, Date destStart,
			Date destEnd, boolean includeSingleAppointmentsAndExceptions,
			Date firstStart) throws RaplaException {
		final RaplaFacade raplaFacade = getFacade();
		User user = getUser();
		Reservation r = raplaFacade.clone(reservation, user);
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
		    app.moveTo( newStart) ;
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

	@Override
	public boolean isEnabled()
	{
		return true;
	}

	private Date getNewStartWeekly(Date oldStart, Date destStart) {
		Date newStart;
		int weekday = DateTools.getWeekday( oldStart);
		Date date = DateTools.setWeekday(destStart, weekday);
		if ( date.before( destStart))
		{
			date = DateTools.addWeeks( date, 1);
		}
		Date firstOccOfWeekday  = date;
		newStart = getRaplaLocale().toDate(  firstOccOfWeekday, oldStart );
		return newStart;
	}




	


}

