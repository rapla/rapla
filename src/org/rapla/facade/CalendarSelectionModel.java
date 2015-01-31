package org.rapla.facade;
import java.util.Collection;
import java.util.Date;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.framework.RaplaException;

public interface CalendarSelectionModel extends CalendarModel{
    String getTitle();

	void setTitle(String title);

	void setViewId(String viewId);

	String getViewId();
		
	void setSelectedObjects(Collection<? extends Object> selectedObjects);

    void setOption( String name, String string );

    void selectUser( User user );
    
    /** If show only own reservations is selected. Thats if the current user is selected with select User*/
    boolean isOnlyCurrentUserSelected();

    void setReservationFilter(ClassificationFilter[] array);

    void setAllocatableFilter(ClassificationFilter[] filters);

    public void resetExports();
    public void save(final String filename) throws RaplaException;
    public void load(final String filename) throws RaplaException, EntityNotFoundException, CalendarNotFoundExeption;
    
    CalendarSelectionModel clone();
	
	void setMarkedIntervals(Collection<TimeInterval> timeIntervals,  boolean timeEnabled);
	/** calls setMarkedIntervals with a single interval from start to end*/
	void markInterval(Date start, Date end);

	void setMarkedAllocatables(Collection<Allocatable> allocatable);

    /** CalendarModels do not update automatically but need to be notified on changes from the outside*/
	void dataChanged(ModificationEvent evt) throws RaplaException;

    boolean isMarkedIntervalTimeEnabled();

}