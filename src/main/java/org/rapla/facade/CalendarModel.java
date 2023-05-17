package org.rapla.facade;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.*;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;


public interface CalendarModel extends Cloneable, ClassifiableFilter
{
    String SHOW_NAVIGATION_ENTRY = "org.rapla.plugin.abstractcalendar.show_navigation";
    String ONLY_ALLOCATION_INFO = "org.rapla.plugin.abstractcalendar.only_allocation_info";
	String SAVE_SELECTED_DATE = "org.rapla.plugin.abstractcalendar.save_selected_date";
    String PAGES = "org.rapla.plugin.abstractcalendar.pages";
    String RESOURCES_LINK_LIST = "org.rapla.plugin.abstractcalendar.resources_link_list";

	String getNonEmptyTitle();

    String getNonEmptyTitle(String annotationName);

    String getFilename();

	User getUser();

    Date getSelectedDate();

    void setSelectedDate( Date date );

    Date getStartDate();

    void setStartDate( Date date );

    Date getEndDate();

    void setEndDate( Date date );

    TimeInterval getTimeIntervall();

    Collection<RaplaObject> getSelectedObjects();


    /** Calendar View Plugins can use the calendar options to store their requiered optional parameters for a calendar view */
    String getOption(String name);
    

    List<Allocatable> getSelectedAllocatablesSorted() throws RaplaException;

    Collection<Allocatable> getSelectedAllocatablesAsList() throws RaplaException;

    //Map<Allocatable,Collection<Reservation>> queryReservations( Date startDate, Date endDate ) throws RaplaException;



    CalendarModel clone();
    
  
    


    DynamicType guessNewEventType() throws RaplaException;
    
	/** returns the marked time intervals in the calendar. */ 
	Collection<TimeInterval> getMarkedIntervals();
    
	Collection<Allocatable> getMarkedAllocatables();

    Promise<Collection<Reservation>> queryReservations( TimeInterval interval );
    Promise<Collection<Appointment>> queryAppointments(TimeInterval interval);
    Promise<AppointmentMapping> queryAppointmentBindings(TimeInterval interval);
    Promise<List<AppointmentBlock>> queryBlocks(TimeInterval timeInterval);

	boolean isMatchingSelectionAndFilter(Reservation reservation, Appointment appointment) throws RaplaException;



}