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

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.datatransfer.StringSelection;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;

import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.Command;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.ReservationCheck;
import org.rapla.gui.ReservationController;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.internal.common.RaplaClipboard;
import org.rapla.gui.internal.edit.DeleteUndo;
import org.rapla.gui.internal.edit.SaveUndo;
import org.rapla.gui.internal.view.HTMLInfo.Row;
import org.rapla.gui.internal.view.ReservationInfoUI;
import org.rapla.gui.toolkit.DialogUI;

public class ReservationControllerImpl extends RaplaGUIComponent
    implements
    ModificationListener, ReservationController
{
	private List<ReservationEditImpl> editWindowList = new ArrayList<ReservationEditImpl>();
	// We store all open ReservationEditWindows with their reservationId in a map to find out if the reservation is already being edited. That prevents editing the same Reservation in different windows

    public ReservationControllerImpl(RaplaContext sm)
    {
        super(sm);
        getUpdateModule().addModificationListener(this);
    }

    void addReservationEdit(ReservationEdit editWindow) {
        editWindowList.add((ReservationEditImpl)editWindow);
    }

    void removeReservationEdit(ReservationEdit editWindow) {
        editWindowList.remove(editWindow);
    }

    public ReservationEdit edit(Reservation reservation) throws RaplaException {
        return startEdit(reservation,null);
    }

    public ReservationEdit edit(AppointmentBlock appointmentBlock)throws RaplaException {
    	return startEdit(appointmentBlock.getAppointment().getReservation(), appointmentBlock);
    }

    public ReservationEdit[] getEditWindows() {
        return  editWindowList.toArray( new ReservationEdit[] {});
    }

    private ReservationEditImpl newEditWindow() throws RaplaException {
        ReservationEditImpl c = new ReservationEditImpl(getContext());
        return c;
    }

    private ReservationEdit startEdit(Reservation reservation,AppointmentBlock appointmentBlock)
        throws RaplaException {
        // Lookup if the reservation is already beeing edited
        ReservationEditImpl c = null;
        Iterator<ReservationEditImpl> it = editWindowList.iterator();
        while (it.hasNext()) {
            c = it.next();
            if (c.getReservation().isIdentical(reservation))
                break;
            else
                c = null;
        }

        if (c != null) {
            c.frame.requestFocus();
            c.frame.toFront();
        } else {
            c = newEditWindow();
            
            // only is allowed to exchange allocations
            c.editReservation(reservation, appointmentBlock);
            if ( !canModify( reservation) ) 
            {
            	c.deleteButton.setEnabled( false);
                disableComponentAndAllChildren(c.appointmentEdit.getComponent());
                disableComponentAndAllChildren(c.reservationInfo.getComponent());
            }
        }
        return c;
    }

    static void disableComponentAndAllChildren(Container component) {
        component.setEnabled( false );
        Component[] components = component.getComponents();
        for ( int i=0; i< components.length; i++)
        {
            if ( components[i] instanceof Container) {
                disableComponentAndAllChildren( (Container) components[i] );
            }
        }
    }

	public void deleteBlocks(Collection<AppointmentBlock> blockList,
			Component parent, Point point) throws RaplaException 
	{
	    DialogUI dlg = getInfoFactory().createDeleteDialog(blockList.toArray(), parent);
        dlg.start();
        if (dlg.getSelectedIndex() != 0)
            return;
        
		Set<Appointment> appointmentsToRemove = new LinkedHashSet<Appointment>();
		HashMap<Appointment,List<Date>> exceptionsToAdd = new LinkedHashMap<Appointment,List<Date>>();
		HashMap<Reservation,Integer> appointmentsRemoved = new LinkedHashMap<Reservation,Integer>();
		Set<Reservation> reservationsToRemove = new LinkedHashSet<Reservation>();
        
		for ( AppointmentBlock block: blockList)
		{
			Appointment appointment = block.getAppointment();
			Date from = new Date(block.getStart());
			Repeating repeating = appointment.getRepeating();
			boolean exceptionsAdded = false;
			if ( repeating != null)
	        {
			    List<Date> dateList = exceptionsToAdd.get( appointment );
			    if ( dateList == null)
                {
                    dateList = new ArrayList<Date>();
                    exceptionsToAdd.put( appointment,dateList);
                }
		        dateList.add(from);
		        if ( isNotEmptyWithExceptions(appointment, dateList))
		        {
		             exceptionsAdded = true;
		        }
		        else
		        {
		            exceptionsToAdd.remove( appointment);
		        }
	        }
			if (!exceptionsAdded)
			{
			    boolean added = appointmentsToRemove.add(appointment);
			    if ( added)
			    {
			        Reservation reservation = appointment.getReservation();
                    Integer count = appointmentsRemoved.get(reservation);
                    if ( count == null)
                    {
                        count = 0;
                    }
                    count++;
                    appointmentsRemoved.put( reservation, count);
			    }
			}
		}

		
		for (Reservation reservation: appointmentsRemoved.keySet())
		{
		    Integer count = appointmentsRemoved.get( reservation);
		    Appointment[] appointments = reservation.getAppointments();
            if ( count == appointments.length)
		    {
		        reservationsToRemove.add( reservation);
		        for (Appointment appointment:appointments)
		        {
		            appointmentsRemoved.remove(appointment);
		        }
		    }
		}
		
	    DeleteBlocksCommand command = new DeleteBlocksCommand(reservationsToRemove, appointmentsToRemove, exceptionsToAdd);
	    CommandHistory commanHistory = getModification().getCommandHistory();
        commanHistory.storeAndExecute( command);
	}

	class DeleteBlocksCommand extends DeleteUndo<Reservation>
	{
	    Set<Reservation> reservationsToRemove;
	    Set<Appointment> appointmentsToRemove; 
	    Map<Appointment, List<Date>> exceptionsToAdd;
	    
	    private Map<Appointment,Allocatable[]> allocatablesRemoved = new HashMap<Appointment,Allocatable[]>();
	    private Map<Appointment,Reservation> parentReservations = new HashMap<Appointment,Reservation>();
	      
	    public DeleteBlocksCommand(Set<Reservation> reservationsToRemove, Set<Appointment> appointmentsToRemove, Map<Appointment, List<Date>> exceptionsToAdd) {
	        super( ReservationControllerImpl.this.getContext(),reservationsToRemove);
	        this.reservationsToRemove = reservationsToRemove;
	        this.appointmentsToRemove = appointmentsToRemove;
	        this.exceptionsToAdd = exceptionsToAdd;
	    }

	    public boolean execute() throws RaplaException {
    	    HashMap<Reservation,Reservation> toUpdate = new LinkedHashMap<Reservation,Reservation>();
    	    allocatablesRemoved.clear();
    	    for (Appointment appointment:appointmentsToRemove)
    	    {
    	        Reservation reservation = appointment.getReservation();
    	        if ( reservationsToRemove.contains( reservation))
    	        {
    	            continue;
    	        }
    	        parentReservations.put(appointment, reservation);
    	        Reservation mutableReservation=  toUpdate.get(reservation);
                if ( mutableReservation == null)
                {
                    mutableReservation = getModification().edit( reservation);
                    toUpdate.put( reservation, mutableReservation);
                }
                Allocatable[] restrictedAllocatables = mutableReservation.getRestrictedAllocatables(appointment);
                mutableReservation.removeAppointment( appointment);
                allocatablesRemoved.put( appointment, restrictedAllocatables);
    	    }
    	    for (Appointment appointment:exceptionsToAdd.keySet())
            {
                Reservation reservation = appointment.getReservation();
                if ( reservationsToRemove.contains( reservation))
                {
                    continue;
                }
                Reservation mutableReservation=  toUpdate.get(reservation);
                if ( mutableReservation == null)
                {
                    mutableReservation = getModification().edit( reservation);
                    toUpdate.put( reservation, mutableReservation);
                }
                Appointment found = mutableReservation.findAppointment( appointment);
                if ( found != null)
                {
                    Repeating repeating = found.getRepeating();
                    if ( repeating != null)
                    {
                        List<Date> list = exceptionsToAdd.get( appointment);
                        for (Date exception: list)
                        {
                            repeating.addException( exception);
                        }
                    }
                }
            }
    	    Reservation[] updateArray = toUpdate.values().toArray(Reservation.RESERVATION_ARRAY);
    		Reservation[] removeArray = reservationsToRemove.toArray( Reservation.RESERVATION_ARRAY);
    		getModification().storeAndRemove(updateArray, removeArray);
    		return true;
        }
	    
	    public boolean undo() throws RaplaException {
	        if (!super.undo())
            {
                return false;
            }
	        HashMap<Reservation,Reservation> toUpdate = new LinkedHashMap<Reservation,Reservation>();
            for (Appointment appointment:appointmentsToRemove)
            {
                Reservation reservation = parentReservations.get(appointment);
                Reservation mutableReservation=  toUpdate.get(reservation);
                if ( mutableReservation == null)
                {
                    mutableReservation = getModification().edit( reservation);
                    toUpdate.put( reservation, mutableReservation);
                }
                mutableReservation.addAppointment( appointment);
                Allocatable[] removedAllocatables = allocatablesRemoved.get( appointment);
                mutableReservation.setRestriction( appointment, removedAllocatables);
            }
            for (Appointment appointment:exceptionsToAdd.keySet())
            {
                Reservation reservation = appointment.getReservation();
                Reservation mutableReservation=  toUpdate.get(reservation);
                if ( mutableReservation == null)
                {
                    mutableReservation = getModification().edit( reservation);
                    toUpdate.put( reservation, mutableReservation);
                }
                Appointment found = mutableReservation.findAppointment( appointment);
                if ( found != null)
                {
                    Repeating repeating = found.getRepeating();
                    if ( repeating != null)
                    {
                        List<Date> list = exceptionsToAdd.get( appointment);
                        for (Date exception: list)
                        {
                            repeating.removeException( exception);
                        }
                    }
                }
            }
          
            Reservation[] updateArray = toUpdate.values().toArray(Reservation.RESERVATION_ARRAY);
            Reservation[] removeArray = Reservation.RESERVATION_ARRAY;
            getModification().storeAndRemove(updateArray,removeArray);
            return true;
	    }
	    
	    public String getCommandoName() 
	    {
	        return getString("delete") + " " + getString("appointments");
	    }
	}

    public void deleteAppointment(AppointmentBlock appointmentBlock, Component sourceComponent, Point point) throws RaplaException {
    	boolean includeEvent = true;
    	Appointment appointment = appointmentBlock.getAppointment();
        final DialogAction dialogResult = showDialog(appointmentBlock, "delete", includeEvent, sourceComponent, point);
    	
		Set<Appointment> appointmentsToRemove = new LinkedHashSet<Appointment>();
        HashMap<Appointment,List<Date>> exceptionsToAdd = new LinkedHashMap<Appointment,List<Date>>();
        Set<Reservation> reservationsToRemove = new LinkedHashSet<Reservation>();
        final Date startDate = new Date(appointmentBlock.getStart());
        switch (dialogResult) {
            case SINGLE:
                Repeating repeating = appointment.getRepeating();
                if ( repeating != null )
                {
                    List<Date> exceptionList = Collections.singletonList( startDate);
                    if ( isNotEmptyWithExceptions(appointment, exceptionList))
                    {
                        exceptionsToAdd.put( appointment,exceptionList);
                    }
                    else
                    {
                        appointmentsToRemove.add( appointment);
                    }
                }
                else
                {
                    appointmentsToRemove.add( appointment);
                }
                break;
            case EVENT:
                reservationsToRemove.add( appointment.getReservation());
                break;
            case SERIE:
                appointmentsToRemove.add( appointment);
                break;
            case CANCEL:
                return;
        }
    
        DeleteBlocksCommand command = new DeleteBlocksCommand(reservationsToRemove, appointmentsToRemove, exceptionsToAdd)
        {
            public String getCommandoName() {
                String name;
                if (dialogResult == DialogAction.SINGLE)
                    name =getI18n().format("single_appointment.format",startDate);
                else if (dialogResult == DialogAction.EVENT)
                    name = getString("reservation");
                else if  (dialogResult == DialogAction.SERIE)
                    name = getString("serie");
                else
                    name = getString("appointment");
                return getString("delete") + " " + name;
            }
        };
        CommandHistory commandHistory = getModification().getCommandHistory();
        commandHistory.storeAndExecute( command );
    }
    
    private boolean isNotEmptyWithExceptions(Appointment appointment, List<Date> exceptions) {
        Repeating repeating = appointment.getRepeating();
        if ( repeating != null)
        {
            
            int number = repeating.getNumber();
            if ( number>=1)
            {
                if (repeating.getExceptions().length >= number-1)
                {
                    Collection<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
                    appointment.createBlocks(appointment.getStart(), appointment.getMaxEnd(), blocks);
                    int blockswithException = 0;
                    for (AppointmentBlock block:blocks)
                    {
                        long start = block.getStart();
                        boolean blocked = false;
                        for (Date excepion: exceptions)
                        {
                            if (DateTools.isSameDay(excepion.getTime(), start))
                            {
                                blocked = true;
                            }
                        }
                        if ( blocked)
                        {
                            blockswithException++;
                        }
                    }
                    if ( blockswithException >= blocks.size())
                    {
                        return false;
                    }
                }
                    
            }
        }
        return true;
    }
    
    public Appointment copyAppointment(Appointment appointment) throws RaplaException {
        return getModification().clone(appointment);
    }

    enum DialogAction
    {
    	EVENT,
    	SERIE,
    	SINGLE,
    	CANCEL
    }
    
    private DialogAction showDialog(AppointmentBlock appointmentBlock
            ,String action
            ,boolean includeEvent
            ,Component sourceComponent
            ,Point point
    		) throws RaplaException
    {
    	Appointment appointment = appointmentBlock.getAppointment();
    	Date from = new Date(appointmentBlock.getStart());
    	Reservation reservation = appointment.getReservation();
        getLogger().debug(action + " '" + appointment + "' for reservation '" +  reservation + "'");
        List<String> optionList = new ArrayList<String>();
        List<Icon> iconList = new ArrayList<Icon>();
        List<DialogAction> actionList = new ArrayList<ReservationControllerImpl.DialogAction>();
        String dateString = getRaplaLocale().formatDate(from);
        
        if ( reservation.getAppointments().length <=1 ||  includeEvent)
        {
        	optionList.add(getString("reservation"));
        	iconList.add(getIcon("icon.edit_window_small"));
        	actionList.add(DialogAction.EVENT);
        }
        if ( appointment.getRepeating() != null && reservation.getAppointments().length > 1 )
        {
        	String shortSummary = getAppointmentFormater().getShortSummary(appointment);
        	optionList.add(getString("serie") + ": " + shortSummary);
        	iconList.add(getIcon("icon.repeating"));
        	actionList.add(DialogAction.SERIE);
        }
        if ( (appointment.getRepeating() != null  && isNotEmptyWithExceptions( appointment, Collections.singletonList(from)))|| reservation.getAppointments().length > 1)
        {
        	optionList.add(getI18n().format("single_appointment.format",dateString));
        	iconList.add(getIcon("icon.single"));
        	actionList.add( DialogAction.SINGLE);
        }
        if (optionList.size() > 1) {
          
			DialogUI dialog = DialogUI.create(
                    getContext()
                    ,sourceComponent
                    ,true
                    ,getString(action)
                    ,getString(action+ "_appointment.format")
                    ,optionList.toArray(new String[] {})
            );
            dialog.setIcon(getIcon("icon.question"));
            for ( int i=0;i< optionList.size();i++)
            {
            	dialog.getButton(i).setIcon(iconList.get( i));
            }
            
            dialog.start(point);
            int index = dialog.getSelectedIndex();
            if ( index < 0)
            {
            	return DialogAction.CANCEL;
            }
            return actionList.get(index);
        }
        else
        {
        	if ( action.equals("delete"))
        	{
        		 DialogUI dlg = getInfoFactory().createDeleteDialog( new Object[]{ appointment.getReservation()}, sourceComponent);
        		 dlg.start();
        		 if (dlg.getSelectedIndex() != 0)
        			 return DialogAction.CANCEL;
        	       
        	}
        }
        if ( actionList.size() > 0)
        {
        	return actionList.get( 0 );
        }
        return DialogAction.EVENT;
    }
   

    public Appointment copyAppointment(
    		                           AppointmentBlock appointmentBlock
                                       ,Component sourceComponent
                                       ,Point point
                                       ,Collection<Allocatable> contextAllocatables
                                       )
        throws RaplaException
    {
    	RaplaClipboard raplaClipboard = getClipboard();
        Appointment appointment = appointmentBlock.getAppointment();
        DialogAction result = showDialog(appointmentBlock, "copy", true, sourceComponent, point);
        Reservation sourceReservation = appointment.getReservation();
       
        // copy info text to system clipboard
        {
	        StringBuffer buf = new StringBuffer();
	        ReservationInfoUI reservationInfoUI = new ReservationInfoUI(getContext());
	    	boolean excludeAdditionalInfos = false;
	    
			List<Row> attributes = reservationInfoUI.getAttributes(sourceReservation, null, null, excludeAdditionalInfos);
			for (Row row:attributes)
			{
				buf.append( row.getField());
			}
			String string = buf.toString();
			
			try
			{
				final IOInterface service = getIOService();
				
			    if (service != null) {
			    	StringSelection transferable = new StringSelection(string);
					
					service.setContents(transferable, null);
			    } 
			}
			catch (AccessControlException ex)
			{
			}
        }
	        
        Allocatable[] restrictedAllocatables = sourceReservation.getRestrictedAllocatables(appointment);
       
        if ( result == DialogAction.SINGLE)
        {
        	Appointment copy = copyAppointment(appointment);
        	copy.setRepeatingEnabled(false);
        	Calendar cal = getRaplaLocale().createCalendar();
        	cal.setTime( copy.getStart());
        	int hour_of_day = cal.get( Calendar.HOUR_OF_DAY);
        	int minute = cal.get( Calendar.MINUTE);
        	int second = cal.get( Calendar.SECOND);
        	cal.setTimeInMillis( appointmentBlock.getStart());
        	cal.set( Calendar.HOUR_OF_DAY, hour_of_day);
        	cal.set( Calendar.MINUTE,minute);
        	cal.set( Calendar.SECOND,second);
        	cal.set( Calendar.MILLISECOND,0);
        	Date newStart = cal.getTime();
        	copy.move(newStart);
        	raplaClipboard.setAppointment(copy, false, sourceReservation, restrictedAllocatables, contextAllocatables);
        	return copy;
        }
        else if ( result == DialogAction.EVENT && appointment.getReservation().getAppointments().length >1)
        {
        	int num  = getAppointmentIndex(appointment);
        	Reservation reservation = appointment.getReservation();
        	Reservation clone = getModification().clone( reservation);
        	Appointment[] clonedAppointments = clone.getAppointments();
        	if ( num >= clonedAppointments.length)
        	{
        		return null;
        	}
        	
        	Appointment clonedAppointment = clonedAppointments[num];
        	boolean wholeReservation = true;
     		raplaClipboard.setAppointment(clonedAppointment, wholeReservation, clone, restrictedAllocatables, contextAllocatables);
     	   
        	return clonedAppointment;
        }
        else
        {
        	Appointment copy = copyAppointment(appointment);
    		raplaClipboard.setAppointment(copy, false, sourceReservation, restrictedAllocatables, contextAllocatables);
        	return copy;
        }
        
    }

	public int getAppointmentIndex(Appointment appointment) {
		int num;
		Reservation reservation = appointment.getReservation();
		num = 0;
		for (Appointment app:reservation.getAppointments())
		{
		
			if ( appointment.equals(app))
			{
				break;
			}
			num++;
		}
		return num;
	}

    public void dataChanged(ModificationEvent evt) throws RaplaException {
    	
    	// we need to clone the list, because it could be modified during edit
        ArrayList<ReservationEditImpl> clone = new ArrayList<ReservationEditImpl>(editWindowList);
        for ( ReservationEditImpl c:clone)
        {
            c.refresh(evt);
            TimeInterval invalidateInterval = evt.getInvalidateInterval();
			Reservation original = c.getOriginal();
			if ( invalidateInterval != null && original != null)
			{
				boolean test = false;
				for (Appointment app:original.getAppointments())
				{
					if ( app.overlaps( invalidateInterval))
					{
						test = true;
					}
					
				}
				if ( test )
				{
					try
					{
						Reservation persistant = getModification().getPersistant( original);
						long version = ((RefEntity<?>)persistant).getVersion();
						long originalVersion = ((RefEntity<?>)original).getVersion();
						if ( originalVersion < version)
						{
							c.updateReservation(persistant);
						}
					} 
					catch (EntityNotFoundException ex)
					{
						c.deleteReservation();	
					}
					
				}
			}
           
        }
    }

	private RaplaClipboard getClipboard() 
	{
        return getService(RaplaClipboard.class);
    }
	
    public boolean isAppointmentOnClipboard() {
        return (getClipboard().getAppointment() != null || !getClipboard().getReservations().isEmpty());
    }
    
    public void pasteAppointment(Date start, Component sourceComponent, Point point, boolean asNewReservation, boolean keepTime) throws RaplaException {
    	RaplaClipboard clipboard = getClipboard();
    
    	Collection<Reservation> reservations = clipboard.getReservations();
    	CommandUndo<RaplaException> pasteCommand;
    	if ( reservations.size() > 1)
    	{
    		pasteCommand = new ReservationPaste(reservations, start);
    	}
    	else
    	{
    		Appointment appointment = clipboard.getAppointment();
        	if (appointment == null) {
        		return;
        	}
	    	Reservation reservation = clipboard.getReservation();
	    	boolean copyWholeReservation = clipboard.isWholeReservation();
	
	    	Allocatable[] restrictedAllocatables = clipboard.getRestrictedAllocatables();
	    	
	    	long offset = getOffset(appointment.getStart(), start, keepTime);
	    	
			
	    	getLogger().debug("Paste appointment '" + appointment 
			          + "' for reservation '" + reservation 
			          + "' at " + start);
			
	    	
	    	Collection<Allocatable> currentlyMarked = getService(CalendarSelectionModel.class).getMarkedAllocatables();
	    	Collection<Allocatable> previouslyMarked = clipboard.getConextAllocatables();
	    	// exchange allocatables if pasted in a different allocatable slot
	    	if ( copyWholeReservation && currentlyMarked != null && previouslyMarked != null && currentlyMarked.size() == 1 && previouslyMarked.size() == 1)
	    	{
	    		Allocatable newAllocatable = currentlyMarked.iterator().next();
	    		Allocatable oldAllocatable = previouslyMarked.iterator().next();
				if ( !newAllocatable.equals( oldAllocatable))
				{
					if ( !reservation.hasAllocated(newAllocatable))
					{
						AppointmentBlock appointmentBlock = new AppointmentBlock(appointment);
						AllocatableExchangeCommand cmd = exchangeAllocatebleCmd(appointmentBlock, oldAllocatable, newAllocatable, sourceComponent, point);
						reservation = cmd.getModifiedReservationForExecute();
						appointment = reservation.getAppointments()[0];
					}
				}
	    	}
	    	pasteCommand = new AppointmentPaste(appointment, reservation, restrictedAllocatables, asNewReservation, copyWholeReservation, offset, sourceComponent);
    	}
    	getClientFacade().getCommandHistory().storeAndExecute(pasteCommand);
    }

    public void moveAppointment(AppointmentBlock appointmentBlock,Date newStart,Component sourceComponent,Point p, boolean keepTime) throws RaplaException {
        Date from = new Date( appointmentBlock.getStart());
    	if ( newStart.equals(from))
            return;
        getLogger().debug("Moving appointment " + appointmentBlock.getAppointment() + " from " + from + " to " + newStart);
        resizeAppointment(appointmentBlock, newStart, null, sourceComponent, p, keepTime);
    }

	public void resizeAppointment(AppointmentBlock appointmentBlock,  Date newStart, Date newEnd, Component sourceComponent, Point p, boolean keepTime) throws RaplaException {
        boolean includeEvent = newEnd == null;
        Appointment appointment = appointmentBlock.getAppointment();
        Date from = new Date(appointmentBlock.getStart());
		DialogAction result = showDialog(appointmentBlock, "move", includeEvent, sourceComponent, p);
		
        if (result == DialogAction.CANCEL) {
        	return;
        }
    	
    	Date oldStart = from;
    	Date oldEnd   = (newEnd == null) ? null : new Date(from.getTime() + appointment.getEnd().getTime() - appointment.getStart().getTime());
        if ( keepTime && newStart != null && !newStart.equals( oldStart))
        {
        	newStart = new Date( oldStart.getTime() + getOffset(oldStart, newStart, keepTime));
        }
        AppointmentResize resizeCommand = new AppointmentResize(appointment, oldStart, oldEnd, newStart, newEnd, sourceComponent, result, keepTime);
		getClientFacade().getCommandHistory().storeAndExecute(resizeCommand);
    }

	public long getOffset(Date appStart, Date newStart, boolean keepTime) {
		Calendar calendar = getRaplaLocale().createCalendar();        		
        calendar.setTime( newStart);
		if ( keepTime)
        {
        	Calendar cal2 = getRaplaLocale().createCalendar();        		
            cal2.setTime( appStart);
        	calendar.set(Calendar.HOUR_OF_DAY, cal2.get( Calendar.HOUR_OF_DAY));
        	calendar.set(Calendar.MINUTE, cal2.get( Calendar.MINUTE));
           	calendar.set(Calendar.SECOND, cal2.get( Calendar.SECOND));
           	calendar.set(Calendar.MILLISECOND, cal2.get( Calendar.MILLISECOND));
        }
        Date newStartAdjusted = calendar.getTime();
        long offset = newStartAdjusted.getTime() - appStart.getTime();
		return offset;
	}

    public boolean save(Reservation reservation, Component sourceComponent) throws RaplaException {
    	SaveCommand saveCommand = new SaveCommand(reservation);
        save(reservation, sourceComponent, saveCommand);
        return saveCommand.hasSaved();
    }  
    
    boolean save(Reservation reservation
              ,Component sourceComponent
              ,Command saveCommand
              ) throws RaplaException {
        Collection<ReservationCheck> checkers = getContainer().lookupServicesFor(RaplaClientExtensionPoints.RESERVATION_SAVE_CHECK);
        for (ReservationCheck check:checkers)
        {
            boolean successful= check.check(reservation, sourceComponent);
            if ( !successful)
            {
                return false;
            }
        }
        try {
            saveCommand.execute();
            return true;
        } catch (Exception ex) {
            showException(ex,sourceComponent);
            return false;
        }
    }
   
    class SaveCommand implements Command {
        private final Reservation reservation;
        boolean saved;
        public SaveCommand(Reservation reservation) {
            this.reservation = reservation;
        }

        public void execute() throws RaplaException {
            getModification().store( reservation );
            saved = true;
        }

        public boolean hasSaved() {
            return saved;
        }
    }

    public void exchangeAllocatable(final AppointmentBlock appointmentBlock,final Allocatable oldAllocatable,final Allocatable newAllocatable,final Component sourceComponent, final Point point)
			 throws RaplaException 
	{
        AllocatableExchangeCommand command = exchangeAllocatebleCmd( appointmentBlock, oldAllocatable, newAllocatable, sourceComponent, point);
        if ( command != null)
        {
        	CommandHistory commandHistory = getModification().getCommandHistory();
			commandHistory.storeAndExecute( command );
        }
	}

	protected AllocatableExchangeCommand exchangeAllocatebleCmd(
			AppointmentBlock appointmentBlock, final Allocatable oldAllocatable,
			final Allocatable newAllocatable, final Component sourceComponent,
			final Point point) throws RaplaException {
		Map<Allocatable,Appointment[]> newRestrictions = new HashMap<Allocatable, Appointment[]>();
        //Appointment appointment;
        //Allocatable oldAllocatable;
        //Allocatable newAllocatable;
        boolean removeAllocatable = false;
        boolean addAllocatable = false;
        Appointment addAppointment = null;
        List<Date> exceptionsAdded = new ArrayList<Date>();
        Appointment appointment = appointmentBlock.getAppointment();
        Reservation reservation = appointment.getReservation();
        Date date = new Date(appointmentBlock.getStart());
        
    	Appointment copy = null;
		Appointment[] restriction = reservation.getRestriction(oldAllocatable);
		boolean includeEvent = restriction.length ==  0;
		DialogAction result = showDialog(appointmentBlock, "exchange_allocatables", includeEvent, sourceComponent, point);
        if (result == DialogAction.CANCEL)
            return null;

        if (result == DialogAction.SINGLE && appointment.getRepeating() != null) {
            copy = copyAppointment(appointment);
            copy.setRepeatingEnabled(false);
            Calendar cal = getRaplaLocale().createCalendar();
            long start = appointment.getStart().getTime();
			int hour = DateTools.getHourOfDay(start);
			int minute = DateTools.getMinuteOfHour(start);
			cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY,hour);
            cal.set(Calendar.MINUTE, minute);
            copy.move(cal.getTime());
        }
        if (result == DialogAction.EVENT && includeEvent )
        {
            removeAllocatable = true;
        	//modifiableReservation.removeAllocatable( oldAllocatable);
        	if ( reservation.hasAllocated( newAllocatable))
        	{
        	    newRestrictions.put( newAllocatable, Appointment.EMPTY_ARRAY);
        	    //modifiableReservation.setRestriction( newAllocatable, Appointment.EMPTY_ARRAY);
        	}
        	else
        	{

        	    addAllocatable = true;
        		//modifiableReservation.addAllocatable(newAllocatable);
        	}
        }
        else
        {
        	Appointment[] apps = reservation.getAppointmentsFor(oldAllocatable);
			if ( copy != null)
			{
			    exceptionsAdded.add(date);
			    //Appointment existingAppointment = modifiableReservation.findAppointment( appointment);
			    //existingAppointment.getRepeating().addException( date );
			    //modifiableReservation.addAppointment( copy);
			    addAppointment = copy;
			    
			    List<Allocatable> all =new ArrayList<Allocatable>(Arrays.asList(reservation.getAllocatablesFor(appointment)));
			    all.remove(oldAllocatable);
			    for ( Allocatable a:all)
			    {
			    	Appointment[] restr = reservation.getRestriction( a);
			    	if ( restr.length > 0)
			    	{
			    		List<Appointment> restrictions = new ArrayList<Appointment>( Arrays.asList( restr));
			    		restrictions.add( copy );
			    		newRestrictions.put(a,  restrictions.toArray(Appointment.EMPTY_ARRAY));
			    		//reservation.setRestriction(a, newRestrictions.toArray(new Appointment[] {}));
			    	}
			    }

			    newRestrictions.put( oldAllocatable, apps);
			    //modifiableReservation.setRestriction(oldAllocatable,apps);
			}
			else
			{
				if ( apps.length == 1)
				{
					//modifiableReservation.removeAllocatable(oldAllocatable);
				    removeAllocatable = true;
				}
				else
				{
					List<Appointment> appointments = new ArrayList<Appointment>(Arrays.asList( apps));
					appointments.remove( appointment);
					newRestrictions.put(oldAllocatable , appointments.toArray(Appointment.EMPTY_ARRAY));
					//modifiableReservation.setRestriction(oldAllocatable, appointments.toArray(Appointment.EMPTY_ARRAY));
				}
			}
			
			Appointment app;
			if ( copy != null)
			{
				app = copy;
			}
			else
			{
			    app = appointment;
			}
			
			
			if ( reservation.hasAllocated( newAllocatable))
			{
				Appointment[] existingRestrictions =reservation.getRestriction(newAllocatable);
				Collection<Appointment> restrictions = new LinkedHashSet<Appointment>( Arrays.asList(existingRestrictions));
				if ( existingRestrictions.length ==0 || restrictions.contains( app))
				{
					// is already allocated, do nothing
				}
				else
				{
					restrictions.add(app); 
				}
				newRestrictions.put( newAllocatable, restrictions.toArray(Appointment.EMPTY_ARRAY));
				//modifiableReservation.setRestriction(newAllocatable, newRestrictions.toArray(Appointment.EMPTY_ARRAY));
			}										
			else
			{
				addAllocatable = true;
			    //modifiableReservation.addAllocatable( newAllocatable);
				if ( reservation.getAppointments().length > 1 || addAppointment != null)
				{
					newRestrictions.put( newAllocatable,new Appointment[] {app});
				    //modifiableReservation.setRestriction(newAllocatable, new Appointment[] {appointment});
				}
			}
        }
        AllocatableExchangeCommand command = new AllocatableExchangeCommand( appointment, oldAllocatable, newAllocatable, newRestrictions, removeAllocatable, addAllocatable, addAppointment, exceptionsAdded);
		return command;
	}

    class AllocatableExchangeCommand implements CommandUndo<RaplaException>
    {
        Appointment appointment;
        Allocatable oldAllocatable;
        Allocatable newAllocatable;
        Map<Allocatable, Appointment[]> newRestrictions;
        Map<Allocatable, Appointment[]> oldRestrictions;
        
        boolean removeAllocatable;
        boolean addAllocatable;
        Appointment addAppointment;
        List<Date> exceptionsAdded;
        
        AllocatableExchangeCommand(Appointment appointment, Allocatable oldAllocatable, Allocatable newAllocatable, Map<Allocatable, Appointment[]> newRestrictions, boolean removeAllocatable, boolean addAllocatable, Appointment addAppointment,
            List<Date> exceptionsAdded)  
        {
            this.appointment = appointment;
            this.oldAllocatable = oldAllocatable;
            this.newAllocatable = newAllocatable;
            this.newRestrictions = newRestrictions;
            this.removeAllocatable = removeAllocatable;
            this.addAllocatable = addAllocatable;
            this.addAppointment = addAppointment;
            this.exceptionsAdded = exceptionsAdded;
        }
        
        public boolean execute() throws RaplaException 
        {
            Reservation modifiableReservation = getModifiedReservationForExecute();
            getModification().store( modifiableReservation);
            return true;
        }

		protected Reservation getModifiedReservationForExecute() throws RaplaException {
			Reservation reservation = appointment.getReservation();
            Reservation modifiableReservation = getModification().edit(reservation);
            if ( addAppointment != null)
            {
                modifiableReservation.addAppointment( addAppointment);
            }
            Appointment existingAppointment = modifiableReservation.findAppointment( appointment);
            if ( existingAppointment != null)
            {
                for ( Date exception: exceptionsAdded)
                {
                    existingAppointment.getRepeating().addException( exception );
                }
            }
            if ( removeAllocatable)
            {
                modifiableReservation.removeAllocatable( oldAllocatable);
            }
            if ( addAllocatable)
            {
                modifiableReservation.addAllocatable(newAllocatable);
            }
            oldRestrictions = new HashMap<Allocatable, Appointment[]>();
            for ( Allocatable alloc: reservation.getAllocatables())
            {
                oldRestrictions.put( alloc, reservation.getRestriction( alloc));
            }
            for ( Allocatable alloc: newRestrictions.keySet())
            {
                Appointment[] restrictions = newRestrictions.get( alloc);
                ArrayList<Appointment> foundAppointments = new ArrayList<Appointment>();
                for ( Appointment app: restrictions)
                {
                    Appointment found = modifiableReservation.findAppointment( app);
                    if ( found != null)
                    {
                        foundAppointments.add( found);
                    }
                }
                modifiableReservation.setRestriction(alloc, foundAppointments.toArray( Appointment.EMPTY_ARRAY));
            }
			return modifiableReservation;
		}
        
        public boolean undo() throws RaplaException 
        {
            Reservation modifiableReservation = getModifiedReservationForUndo();
            getModification().store( modifiableReservation);
            return true;
        }

		protected Reservation getModifiedReservationForUndo()
				throws RaplaException {
			Reservation persistant = getModification().getPersistant(appointment.getReservation());
            Reservation modifiableReservation = getModification().edit(persistant);
            if ( addAppointment != null)
            {
                Appointment found = modifiableReservation.findAppointment( addAppointment );
                if ( found != null)
                {
                    modifiableReservation.removeAppointment( found );
                }
            }
            
            Appointment existingAppointment = modifiableReservation.findAppointment( appointment);
            if ( existingAppointment != null)
            {
                for ( Date exception: exceptionsAdded)
                {
                    existingAppointment.getRepeating().removeException( exception );
                }
            }
            if ( removeAllocatable)
            {
                modifiableReservation.addAllocatable( oldAllocatable);
            }
            if ( addAllocatable)
            {
                modifiableReservation.removeAllocatable(newAllocatable);
            }

            for ( Allocatable alloc: oldRestrictions.keySet())
            {
                Appointment[] restrictions = oldRestrictions.get( alloc);
                ArrayList<Appointment> foundAppointments = new ArrayList<Appointment>();
                for ( Appointment app: restrictions)
                {
                    Appointment found = modifiableReservation.findAppointment( app);
                    if ( found != null)
                    {
                        foundAppointments.add( found);
                    }
                }
                modifiableReservation.setRestriction(alloc, foundAppointments.toArray( Appointment.EMPTY_ARRAY));
            }
			return modifiableReservation;
		}
        
        public String getCommandoName() 
        {
            return getString("exchange_allocatables");
        }
    }
    
	/**
	 * This class collects any information of an appointment that is resized or moved in any way 
	 * in the calendar view.
	 * This is where undo/redo for moving or resizing of an appointment 
	 * in the calendar view is realized. 
	 * @author Jens Fritz
	 *
	 */
    
    //Erstellt und bearbeitet von Dominik Krickl-Vorreiter und Jens Fritz
    class AppointmentResize implements CommandUndo<RaplaException> {
    	
    	private final Date oldStart;
    	private final Date oldEnd;
    	private final Date newStart;
    	private final Date newEnd;
    	
    	private final Appointment appointment;
    	private final Component sourceComponent;
    	private final DialogAction dialogResult;
    	
    	private Appointment lastCopy;
		private boolean firstTimeCall = true;
		private boolean keepTime;

    	public AppointmentResize(Appointment appointment, Date oldStart, Date oldEnd, Date newStart, Date newEnd, Component sourceComponent, DialogAction dialogResult, boolean keepTime) {
        	this.oldStart        = oldStart;
        	this.oldEnd          = oldEnd;
        	this.newStart        = newStart;
        	this.newEnd          = newEnd;
        	
        	this.appointment     = appointment;
        	this.sourceComponent = sourceComponent;
        	this.dialogResult    = dialogResult;
        	this.keepTime = keepTime;
        	lastCopy = null;
    	}
    	
    	public boolean execute() throws RaplaException {
    		boolean resizing = newEnd != null;
    		
    		Date sourceStart = oldStart;
    		Date destStart = newStart;
    		Date destEnd = newEnd;

    		return doMove(resizing, sourceStart, destStart, destEnd, false);
    	}

    	public boolean undo() throws RaplaException { 
    		boolean resizing = newEnd != null;
    		
    		Date sourceStart = newStart;
    		Date destStart = oldStart;
    		Date destEnd = oldEnd;

    		return doMove(resizing, sourceStart, destStart, destEnd, true);
    	}

		private boolean doMove(boolean resizing, Date sourceStart,
				Date destStart, Date destEnd, boolean undo) throws RaplaException {
			Reservation reservation        = appointment.getReservation();
            Reservation mutableReservation = getModification().edit(reservation);
            Appointment mutableAppointment = mutableReservation.findAppointment(appointment);
            
            if (mutableAppointment == null) {
                throw new IllegalStateException("Can't find the appointment: " + appointment);
            }

			long offset = getOffset(sourceStart, destStart, keepTime);
            
        	Collection<Appointment> appointments;
        	
        	// Move the complete serie
        	switch (dialogResult) {
				case SERIE:
					// Wir wollen eine Serie (Appointment mit Wdh) verschieben
					appointments = Collections.singleton(mutableAppointment);
					break;
				case EVENT:
					// Wir wollen die ganze Reservation verschieben
					appointments = Arrays.asList(mutableReservation.getAppointments());
					break;
				case SINGLE:
					// Wir wollen nur ein Appointment aus einer Serie verschieben --> losl_sen von Serie
					
					Repeating repeating = mutableAppointment.getRepeating();
	    			if (repeating == null) {
	    				appointments = Arrays.asList(mutableAppointment);
	    			}
	    			else
	    			{
	    				if (undo)
	    				{
		    				mutableReservation.removeAppointment(lastCopy);
		    				repeating.removeException(oldStart);
		    				lastCopy = null;
		    				return save(mutableReservation, sourceComponent);
	    				}
	    				else
	    				{
	    					lastCopy = copyAppointment(mutableAppointment);
	    					lastCopy.setRepeatingEnabled(false);
	    					appointments = Arrays.asList(lastCopy);
	    				}
	    			}
	    			
	    			break;
				default:
					throw new IllegalStateException("Dialog choice not supported "+ dialogResult ) ;
			}
            
            Date changeStart;
            Date changeEnd;
            
        	for (Appointment ap : appointments) {
        		long startTime = (dialogResult == DialogAction.SINGLE) ? sourceStart.getTime() : ap.getStart().getTime();
        		
        		changeStart = new Date(startTime + offset);
        		
    			if (resizing) {
					changeEnd = new Date(changeStart.getTime() + (destEnd.getTime() - destStart.getTime()));
                    ap.move(changeStart, changeEnd);
                } else {
                    ap.move(changeStart);
                }
        	}
        	
        	if ( !undo)
        	{
	        	if (dialogResult == DialogAction.SINGLE) {
	        		Repeating repeating = mutableAppointment.getRepeating();
	                
	    			if (repeating != null) {
	    				Allocatable[] restrictedAllocatables = mutableReservation.getRestrictedAllocatables(mutableAppointment);
	        			mutableReservation.addAppointment(lastCopy);
	        			mutableReservation.setRestriction(lastCopy, restrictedAllocatables);
	    				repeating.addException(oldStart);
	    			}
	            }
        	}
        	
        	if ( firstTimeCall)
			{
				firstTimeCall = false;
				return save(mutableReservation, sourceComponent);
			}
			else
			{
				getModification().store( mutableReservation );
				return true;
			}
		}
		
		public String getCommandoName() {
			return getString("move");
		}
    }
    
    
    /**
     * This class collects any information of an appointment that is copied and pasted 
     * in the calendar view.
     * This is where undo/redo for pasting an appointment 
     * in the calendar view is realized. 
     * @author Jens Fritz
     *
     */    
    
    //Erstellt von Dominik Krickl-Vorreiter    
    class AppointmentPaste implements CommandUndo<RaplaException> {

		private final Appointment fromAppointment;
		private final Reservation fromReservation;
		private final Allocatable[] restrictedAllocatables;
		private final boolean asNewReservation;
		private final boolean copyWholeReservation;
		private final long offset;
		private final Component sourceComponent;
		
		private Reservation saveReservation = null;
		private Appointment saveAppointment = null;
		private boolean firstTimeCall = true;
		
		public AppointmentPaste(Appointment fromAppointment, Reservation fromReservation, Allocatable[] restrictedAllocatables, boolean asNewReservation, boolean copyWholeReservation, long offset, Component sourceComponent) {
			this.fromAppointment        = fromAppointment;
			this.fromReservation        = fromReservation;
			this.restrictedAllocatables = restrictedAllocatables;
			this.asNewReservation       = asNewReservation;
			this.copyWholeReservation   = copyWholeReservation;
			this.offset                 = offset;
			this.sourceComponent        = sourceComponent;
			
			assert !(!asNewReservation && copyWholeReservation);
		}
		
		public boolean execute() throws RaplaException {
			Reservation mutableReservation = null;
			
			if (asNewReservation) {
				if (saveReservation == null) {
					mutableReservation =  getModification().clone(fromReservation);	
				} else {
					mutableReservation = saveReservation;
				}
	        	
	        	// Alle anderen Appointments verschieben / entfernen
	            Appointment[] appointments = mutableReservation.getAppointments();
	            
	            for (int i=0; i < appointments.length; i++) {
	                Appointment app = appointments[i];
	                
	                if (copyWholeReservation) {
	                	if (saveReservation == null) {
	                		app.move(new Date(app.getStart().getTime() + offset));
	                	}
                	} else {
	                	mutableReservation.removeAppointment(app);
	                }
	            }
	        } else {
				mutableReservation =  getModification().edit(fromReservation);
	        }
			
			if (!copyWholeReservation) {
				if (saveAppointment == null) {
					saveAppointment = copyAppointment(fromAppointment);	
					saveAppointment.move(new Date(saveAppointment.getStart().getTime() + offset));
		        }
				mutableReservation.addAppointment(saveAppointment);
				mutableReservation.setRestriction(saveAppointment, restrictedAllocatables);
	        }

			saveReservation = mutableReservation;
			if ( firstTimeCall)
			{
				firstTimeCall = false;
				return save(mutableReservation, sourceComponent);
			}
			else
			{
				getModification().store( mutableReservation );
				return true;
			}
		}

		public boolean undo() throws RaplaException {			
			if (asNewReservation) {
				Reservation mutableReservation = getModification().edit(saveReservation);
				getModification().remove(mutableReservation);
				return true;
			} else {
				Reservation mutableReservation = getModification().edit(saveReservation);
				mutableReservation.removeAppointment(saveAppointment);
	            getModification().store(mutableReservation);
				return true;
			}
		}
		
		public String getCommandoName() 
		{
			return getString("paste");
		}	
    	
    }
    
    
    class ReservationPaste implements CommandUndo<RaplaException> {

		private final Collection<Reservation> fromReservation;
		Date start;
		Reservation[] array;
		
		public ReservationPaste(Collection<Reservation> fromReservation,Date start) {
			this.fromReservation        = fromReservation;
			this.start = start;
		}
		
		public boolean execute() throws RaplaException {
			List<Entity<Reservation>> clones = copy(fromReservation,start);
			array = clones.toArray(Reservation.RESERVATION_ARRAY);
			getModification().storeAndRemove(array , Reservation.RESERVATION_ARRAY);
			return true;
		}

		public boolean undo() throws RaplaException {			
			getModification().storeAndRemove(Reservation.RESERVATION_ARRAY,array );
			return true;
		}	
		
		public String getCommandoName() 
		{
			return getString("paste");
		}	
    	
    }
    
	/**
	 * This class collects any information of an appointment that is saved 
	 * to the calendar view.
	 * This is where undo/redo for saving an appointment 
	 * in the calendar view is realized. 
	 * @author Jens Fritz
	 *
	 */
    
    //Erstellt von Dominik Krickl-Vorreiter
    class ReservationSave extends SaveUndo<Reservation> {
    	
    	private final Component sourceComponent;
    	Reservation newReservation;

    	public ReservationSave(Reservation newReservation, Reservation original, Component sourceComponent)
    	{
    		super(ReservationControllerImpl.this.getContext(),Collections.singletonList(newReservation), original != null ? Collections.singletonList(original): null);
    		this.sourceComponent  = sourceComponent;
    		this.newReservation = newReservation;
    	}
    	
		public boolean execute() throws RaplaException
		{
			if ( firstTimeCall)
			{
				firstTimeCall = false;
				return save(newReservation, sourceComponent, new SaveCommand(newReservation));
			}
			else
			{
				return super.execute();
			}
		}
    	
    }
}



