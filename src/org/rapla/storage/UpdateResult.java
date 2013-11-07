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
package org.rapla.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;

public class UpdateResult implements ModificationEvent
{
    private User user;
    private List<UpdateOperation> operations = new ArrayList<UpdateOperation>();
	Set<RaplaType> modified = new HashSet<RaplaType>(); 
	
	public UpdateResult(User user) {
        this.user = user;
    }
	
    public void addOperation(final UpdateOperation operation) {
        if ( operation == null)
            throw new IllegalStateException( "Operation can't be null" );
        operations.add(operation);
        RaplaObject current = operation.getCurrent();
        if ( current != null)
        {
        	RaplaType raplaType = current.getRaplaType();
        	modified.add( raplaType);
        }
    }
    
    public User getUser() {
        return user;
    }
    
    public Set<RaplaObject> getRemoved() {
        return getObject( Remove.class);
    }

    public Set<RaplaObject> getChangeObjects() {
        return getObject( Change.class);
    }

    public Set<RaplaObject> getAddObjects() {
        return getObject( Add.class);
    }
    
    @SuppressWarnings("unchecked")
	public <T extends UpdateOperation> Iterator<T> getOperations( final Class<T> operationClass) {
        Iterator<UpdateOperation> operationsIt =  operations.iterator();
        if ( operationClass == null)
            throw new IllegalStateException( "OperationClass can't be null" );
        
        List<T> list = new ArrayList<T>();
        while ( operationsIt.hasNext() ) {
            UpdateOperation obj = operationsIt.next();
            if ( operationClass.isInstance( obj ))
                list.add( (T)obj );
        }
        
        return list.iterator();
    }
    
    public Iterable<UpdateOperation> getOperations()
    {
    	return Collections.unmodifiableCollection(operations);
    }

    protected <T extends UpdateOperation> Set<RaplaObject> getObject( final Class<T> operationClass ) {
        Set<RaplaObject> set = new HashSet<RaplaObject>();
        if ( operationClass == null)
            throw new IllegalStateException( "OperationClass can't be null" );
        Iterator<? extends UpdateOperation> it= getOperations( operationClass);
        while (it.hasNext() ) {
            UpdateOperation next = it.next();
            RaplaObject current = next.getCurrent();
			set.add( current);
        }
        return set;
    }
    
    static public class Add implements UpdateOperation {
    	RaplaObject newObj; // the object in the state when it was added
        public Add( RaplaObject newObj) {
            this.newObj = newObj;
        }
        public RaplaObject getCurrent() {
            return newObj;
        }
        public RaplaObject getNew() {
            return newObj;
        }
        
        public String toString()
        {
        	return "Add " + newObj;
        }
    }

    static public class Remove implements UpdateOperation {
    	RaplaObject currentObj; // the actual represantation of the object
        public Remove(RaplaObject currentObj) {
            this.currentObj = currentObj;
        }
        public RaplaObject getCurrent() {
            return currentObj;
        }
        public String toString()
        {
        	return "Remove " + currentObj;
        }

    }
    
    static public class Change implements UpdateOperation{
    	RaplaObject newObj; // the object in the state when it was changed
    	RaplaObject oldObj; // the object in the state before it was changed
        public Change( RaplaObject newObj, RaplaObject oldObj) {
            this.newObj = newObj;
            this.oldObj = oldObj;
        }
        public RaplaObject getCurrent() {
            return newObj;
        }
        public RaplaObject getNew() {
            return newObj;
        }
        public RaplaObject getOld() {
            return oldObj;
        }
        
        public String toString()
        {
        	return "Change " + oldObj  + " to " + newObj;
        }
    }
    
    TimeInterval timeInterval;
    
    public void setInvalidateInterval(TimeInterval timeInterval)
    {
    	this.timeInterval = timeInterval;
    }
    
	public TimeInterval getInvalidateInterval() 
	{
		return timeInterval;
	}


    public TimeInterval calulateInvalidateInterval() {
		TimeInterval currentInterval = null;
		{
			Iterator<Change> operations = getOperations( Change.class);
			while (operations.hasNext())
			{
				Change change = operations.next();
				currentInterval = expandInterval( change.getNew(), currentInterval);
				currentInterval = expandInterval( change.getOld(), currentInterval);
			}
		}
		{
			Iterator<Add> operations = getOperations( Add.class);
			while (operations.hasNext())
			{
				Add change = operations.next();
	    		currentInterval = expandInterval( change.getNew(), currentInterval);
			}
		}
		{
			Iterator<Remove> operations = getOperations( Remove.class);
			while (operations.hasNext())
			{
				Remove change = operations.next();
	    		currentInterval = expandInterval( change.getCurrent(), currentInterval);
			}
		}
		return currentInterval;
    }
    
	private TimeInterval expandInterval(RaplaObject obj,
			TimeInterval currentInterval) 
	{
		RaplaType type = obj.getRaplaType();
		if ( type == Appointment.TYPE)
		{
			currentInterval = invalidateInterval( currentInterval, (Appointment) obj);
		}
		if ( type == Reservation.TYPE)
		{
			for ( Appointment app:((Reservation)obj).getAppointments())
			{
				currentInterval = invalidateInterval( currentInterval, app);
			}
		}
		return currentInterval;
	}
    
	private TimeInterval invalidateInterval(TimeInterval oldInterval,Appointment appointment) 
    {
		Date start = appointment.getStart();
		Date end = appointment.getMaxEnd();
		TimeInterval interval = new TimeInterval(start, end).union( oldInterval);
    	return interval;
    }

	public boolean hasChanged(RaplaObject object) {
		return getChanged().contains(object);
	}

	public boolean isRemoved(RaplaObject object) {
		return getRemoved().contains( object);
	}

	public boolean isModified(RaplaObject object) 
	{
		return hasChanged(object) || isRemoved( object);
	}

	/** returns the modified objects from a given set.
     * @deprecated use the retainObjects instead in combination with getChanged*/
    public <T extends RaplaObject> Set<T> getChanged(Collection<T> col) {
        return RaplaType.retainObjects(getChanged(),col);
    }

    /** returns the modified objects from a given set.
     * @deprecated use the retainObjects instead in combination with getChanged*/
    public <T extends RaplaObject> Set<T> getRemoved(Collection<T> col) {
        return RaplaType.retainObjects(getRemoved(),col);
    }

	public Set<RaplaObject> getChanged() {
		Set<RaplaObject> result  = new HashSet<RaplaObject>(getAddObjects());
		result.addAll(getChangeObjects());
		return result;
	}

	public boolean isModified(RaplaType raplaType) 
	{
		return modified.contains( raplaType) ;
	}

	public boolean isModified() {
		return !operations.isEmpty();
	}

}
    

