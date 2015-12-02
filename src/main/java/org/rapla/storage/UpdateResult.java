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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.facade.ModificationEvent;

public class UpdateResult implements ModificationEvent
{
    private List<UpdateOperation> operations = new ArrayList<UpdateOperation>();
	Set<RaplaType> modified = new HashSet<RaplaType>();
    Set<EntityReferencer.ReferenceInfo> removedReferences = new HashSet<EntityReferencer.ReferenceInfo>();
	boolean switchTemplateMode = false;
	// FIXME determine
	final Date since = null;
	final Date untill = null;
	
	public UpdateResult() {
    }
	
    public void addOperation(final UpdateOperation operation) {
        if ( operation == null)
            throw new IllegalStateException( "Operation can't be null" );
        operations.add(operation);
        RaplaType raplaType = operation.getRaplaType();
        if ( raplaType != null)
        {
        	modified.add( raplaType);
        }
        if(operation instanceof Remove){
            // FIXME
        }
    }
    
    @Override public Set<EntityReferencer.ReferenceInfo> getRemovedReferences()
    {
        return removedReferences;
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

    public Collection<HistoryEntry> getUnresolvedHistoryEntry(String id)
    {
        // FIXME
        return null;
    }
    
    public HistoryEntry getLastEntryBeforeUpdate(String id)
    {
        // FIXME
        // TODO Conflict resolution
        return null;
    }

    public Entity getLastKnown(String id)
    {
        // FIXME
        // TODO Conflict resolution
        return null;
    }

    protected <T extends UpdateOperation> Set<Entity> getObject( final Class<T> operationClass ) {
        Set<Entity> set = new HashSet<Entity>();
        if ( operationClass == null)
            throw new IllegalStateException( "OperationClass can't be null" );
        Collection<? extends UpdateOperation> it= getOperations( operationClass);
        for (UpdateOperation next:it ) {
            // FIXME
             String currentId =next.getCurrentId();
             final Entity current = getLastKnown(currentId);
             set.add( current);
        }
        return set;
    }
    
    static public class Add implements UpdateOperation {
        private final RaplaType<?> type;
        private final String id;

        public Add( String id, RaplaType<?> type) {
            this.id = id;
            this.type = type;
        }
        
        public String toString()
        {
        	return "Add " + id;
        }

        @Override public String getCurrentId()
        {
            return id;
        }

        @Override public RaplaType getRaplaType()
        {
            return type;
        }
    }

    static public class Remove implements UpdateOperation {
        private String currentId;
        private RaplaType type;

        public Remove(String currentId, RaplaType type) {
            this.currentId = currentId;
            this.type = type;
        }

        @Override public String getCurrentId()
        {
            return currentId;
        }

        @Override public RaplaType getRaplaType()
        {
            return type;
        }

        public String toString()
        {
        	return "Remove " + currentId;
        }

    }

    static public class Change implements UpdateOperation{
        
        private final String id;
        private final RaplaType<?> type;

        public Change( String id, RaplaType<?> type) {
            this.id = id;
            this.type = type;
        }

        @Override public String getCurrentId()
        {
            return id;
        }

        @Override public RaplaType<?> getRaplaType()
        {
            return type;
        }

        public String toString()
        {
        	return "Change " + id;//  + " to " + newObj;
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


	public boolean hasChanged(Entity object) {
		return getChanged().contains(object);
	}

	public boolean isRemoved(Entity object) {
        final EntityReferencer.ReferenceInfo referenceInfo = new EntityReferencer.ReferenceInfo(object);
        return getRemovedReferences().contains( referenceInfo);
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
    

    public static class HistoryEntry
    {
        //EntityReferencer.ReferenceInfo info;
        Entity unresolvedEntity;
        Date timestamp;

        public Entity getUnresolvedEntity()
        {
            return unresolvedEntity;
        }

        public Date getTimestamp()
        {
            return timestamp;
        }
    }


    public Collection<String> getAddedAndChangedIds()
    {
        Set<String> result = new LinkedHashSet<String>();
        fillIds(result, Add.class);
        fillIds(result, Change.class);
        return result;
    }

    private <T extends UpdateOperation> void fillIds(Set<String> result, final Class<T> operationClass)
    {
        final Collection<T> operations = getOperations(operationClass);
        for (UpdateOperation operation : operations)
        {
            result.add(operation.getCurrentId());
        }
    }

    public <T extends UpdateOperation> Collection<String> getIds(final Class<T> operationClass)
    {
        final LinkedHashSet<String> result = new LinkedHashSet<String>();
        fillIds(result, operationClass);
        return result;
    }

}
    

