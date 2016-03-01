package org.rapla.facade;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface CalendarModel extends Cloneable, ClassifiableFilter 
{
    String SHOW_NAVIGATION_ENTRY = "org.rapla.plugin.abstractcalendar.show_navigation";
    String ONLY_ALLOCATION_INFO = "org.rapla.plugin.abstractcalendar.only_allocation_info";
	String SAVE_SELECTED_DATE = "org.rapla.plugin.abstractcalendar.save_selected_date";
	String ONLY_MY_EVENTS = "only_own_reservations";
	TypedComponentRole<Boolean> ONLY_MY_EVENTS_DEFAULT = new TypedComponentRole<Boolean>("org.rapla.plugin.abstractcalendar.only_own_reservations");

	String getNonEmptyTitle();

    User getUser();

    Date getSelectedDate();

    void setSelectedDate( Date date );

    Date getStartDate();

    void setStartDate( Date date );

    Date getEndDate();

    void setEndDate( Date date );

    Collection<RaplaObject> getSelectedObjects();


    /** Calendar View Plugins can use the calendar options to store their requiered optional parameters for a calendar view */
    String getOption(String name);
    

    List<Allocatable> getSelectedAllocatablesSorted();

    Collection<Allocatable> getSelectedAllocatablesAsList();

    //Map<Allocatable,Collection<Reservation>> queryReservations( Date startDate, Date endDate ) throws RaplaException;

    /** use get */
    @Deprecated
    Reservation[] getReservations( Date startDate, Date endDate ) throws RaplaException;

    Reservation[] getReservations() throws RaplaException;

    CalendarModel clone();
    
  
    
    List<AppointmentBlock> getBlocks() throws RaplaException;
    
    DynamicType guessNewEventType() throws RaplaException;
    
	/** returns the marked time intervals in the calendar. */ 
	Collection<TimeInterval> getMarkedIntervals();
    
	Collection<Allocatable> getMarkedAllocatables();

    Collection<Appointment> getAppointments(TimeInterval interval) throws RaplaException;
	
	boolean isMatchingSelectionAndFilter(Reservation reservation, Appointment appointment) throws RaplaException;

    Map<Allocatable,Collection<Appointment>> queryAppointments(Date startDate, Date endDate);

}