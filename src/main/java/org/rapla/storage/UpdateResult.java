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
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.facade.ModificationEvent;

public class UpdateResult implements ModificationEvent
{
    private User user;
    private List<UpdateOperation> operations = new ArrayList<UpdateOperation>();
	Set<RaplaType> modified = new HashSet<RaplaType>(); 
	boolean switchTemplateMode = false;
	
	public UpdateResult(User user) {
        this.user = user;
    }
	
    public void addOperation(final UpdateOperation operation) {
        if ( operation == null)
            throw new IllegalStateException( "Operation can't be null" );
        operations.add(operation);
        Entity current = operation.getCurrent();
        if ( current != null)
        {
        	RaplaType raplaType = current.getRaplaType();
        	modified.add( raplaType);
        }
    }
    
    public User getUser() {
        return user;
    }
    
    public Set<Entity> getRemoved() {
        return getObject( Remove.class);
    }

    public Set<Entity> getChangeObjects() {
        return getObject( Change.class);
    }

    public Set<Entity> getAddObjects() {
        return getObject( Add.class);
    }
    
    @SuppressWarnings("unchecked")
	public <T extends UpdateOperation> Collection<T> getOperations( final Class<T> operationClass) {
        Iterator<UpdateOperation> operationsIt =  operations.iterator();
        if ( operationClass == null)
            throw new IllegalStateException( "OperationClass can't be null" );
        
        List<T> list = new ArrayList<T>();
        while ( operationsIt.hasNext() ) {
            UpdateOperation obj = operationsIt.next();
            if ( operationClass.equals( obj.getClass()))
            {
                list.add( (T)obj );
            }
        }
        
        return list;
    }
    
    public Iterable<UpdateOperation> getOperations()
    {
    	return Collections.unmodifiableCollection(operations);
    }

    protected <T extends UpdateOperation> Set<Entity> getObject( final Class<T> operationClass ) {
        Set<Entity> set = new HashSet<Entity>();
        if ( operationClass == null)
            throw new IllegalStateException( "OperationClass can't be null" );
        Collection<? extends UpdateOperation> it= getOperations( operationClass);
        for (UpdateOperation next:it ) {
            Entity current = next.getCurrent();
			set.add( current);
        }
        return set;
    }
    
    static public class Add implements UpdateOperation {
    	Entity newObj; // the object in the state when it was added
        public Add( Entity newObj) {
            this.newObj = newObj;
        }
        public Entity getCurrent() {
            return newObj;
        }
        public Entity getNew() {
            return newObj;
        }
        
        public String toString()
        {
        	return "Add " + newObj;
        }
    }

    static public class Remove implements UpdateOperation {
    	Entity currentObj; // the actual represantation of the object
        public Remove(Entity currentObj) {
            this.currentObj = currentObj;
        }
        public Entity getCurrent() {
            return currentObj;
        }
        public String toString()
        {
        	return "Remove " + currentObj;
        }

    }
    
    static public class Change implements UpdateOperation{
    	Entity newObj; // the object in the state when it was changed
    	Entity oldObj; // the object in the state before it was changed
        public Change( Entity newObj, Entity oldObj) {
            this.newObj = newObj;
            this.oldObj = oldObj;
        }
        public Entity getCurrent() {
            return newObj;
        }
        public Entity getNew() {
            return newObj;
        }
        public Entity getOld() {
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
			Collection<Change> operations = getOperations( Change.class);
			for (Change change:operations)
			{
				currentInterval = expandInterval( change.getNew(), currentInterval);
				currentInterval = expandInterval( change.getOld(), currentInterval);
			}
		}
		{
			Collection<Add> operations = getOperations( Add.class);
			for (Add change:operations)
			{
	    		currentInterval = expandInterval( change.getNew(), currentInterval);
			}
		}
		{
			Collection<Remove> operations = getOperations( Remove.class);
			for (Remove change:operations)
			{
	    		currentInterval = expandInterval( change.getCurrent(), currentInterval);
			}
		}
		return currentInterval;
    }
    
	private TimeInterval expandInterval(RaplaObject obj,
			TimeInterval currentInterval) 
	{
		RaplaType type = obj.getRaplaType();
		if ( type == Reservation.TYPE)
		{
			for ( Appointment app:((ReservationImpl)obj).getAppointmentList())
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

	public boolean hasChanged(Entity object) {
		return getChanged().contains(object);
	}

	public boolean isRemoved(Entity object) {
		return getRemoved().contains( object);
	}

	public boolean isModified(Entity object) 
	{
		return hasChanged(object) || isRemoved( object);
	}

	/** returns the modified objects from a given set.
     * @deprecated use the retainObjects instead in combination with getChanged*/
    public <T extends RaplaObject> Set<T> getChanged(Collection<T> col) {
        return RaplaType.retainObjects(getChanged(),col);
    }

//    /** returns the modified objects from a given set.
//     * @deprecated use the retainObjects instead in combination with getChanged*/
//    public <T extends RaplaObject> Set<T> getRemoved(Collection<T> col) {
//        return RaplaType.retainObjects(getRemoved(),col);
//    }

	public Set<Entity> getChanged() {
		Set<Entity> result  = new HashSet<Entity>(getAddObjects());
		result.addAll(getChangeObjects());
		return result;
	}

	public boolean isModified(RaplaType raplaType) 
	{
		return modified.contains( raplaType) ;
	}
	
	public boolean isModified() {
		return !operations.isEmpty() || switchTemplateMode;
	}

    public boolean isEmpty() {
        return !isModified() && timeInterval == null;
    }

    public void setSwitchTemplateMode(boolean b) 
    {
        switchTemplateMode = b;
    }
    
    public boolean isSwitchTemplateMode() {
        return switchTemplateMode;
    }

}
    

