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

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.storage.EntityReferencer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpdateResult
{
    private List<UpdateOperation> operations = new ArrayList<UpdateOperation>();
	//Set<RaplaType> modified = new HashSet<RaplaType>();
	private final Date since;
	private final Date until;
    private final Map<String, Entity> oldEntities;
    private final Map<String, Entity> updatedEntities;


    public UpdateResult(Date since, Date until, Map<String, Entity> oldEntities,Map<String, Entity> updatedEntities)
    {
        this.since = since;
        this.until = until;
        this.oldEntities = oldEntities;
        this.updatedEntities= updatedEntities;
    }

    public void addOperation(final UpdateOperation operation) {
        if ( operation == null)
            throw new IllegalStateException( "Operation can't be null" );
        operations.add(operation);
    }

    public Date getSince()
    {
        return since;
    }

    public Date getUntil()
    {
        return until;
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

    /** returns null if no entity exisits before update*/
    public Entity getLastEntryBeforeUpdate(String id)
    {
        // FIXME Conflict resolution
        final Entity entity = oldEntities.get(id);
        return entity;
    }

    public Entity getLastKnown(String id)
    {
        // FIXME Conflict resolution
        return updatedEntities.get( id );
    }

    public void addOperation(Entity<?> newEntity, Entity<?> oldEntity, UpdateOperation operation)
    {
        addOperation(operation);
        if(operation instanceof Remove)
        {
            oldEntities.put(oldEntity.getId(), oldEntity);
        }
        else if(operation instanceof Change)
        {
            oldEntities.put(oldEntity.getId(), oldEntity);
            updatedEntities.put(newEntity.getId(), newEntity);
        }
        else if(operation instanceof Add)
        {
            updatedEntities.put(newEntity.getId(), newEntity);
        }
    }

    static public class Add implements UpdateOperation {
        EntityReferencer.ReferenceInfo info;

        public Add( EntityReferencer.ReferenceInfo info) {
            this.info = info;
        }

        @Override public EntityReferencer.ReferenceInfo getReference()
        {
            return info;
        }
        
        public String toString()
        {
        	return "Add " + getCurrentId();
        }

        @Override public String getCurrentId()
        {
            return info.getId();
        }

        @Override public Class<? extends Entity> getType()
        {
            return info.getType();
        }
    }

    static public class Remove implements UpdateOperation {
        EntityReferencer.ReferenceInfo info;

        public Remove(EntityReferencer.ReferenceInfo info) {
            this.info = info;
        }

        @Override public EntityReferencer.ReferenceInfo getReference()
        {
            return info;
        }

        @Override public String getCurrentId()
        {
            return info.getId();
        }

        @Override public Class<? extends Entity> getType()
        {
            return info.getType();
        }

        public String toString()
        {
        	return "Remove " + getCurrentId();
        }

    }

    static public class Change implements UpdateOperation{
        
        EntityReferencer.ReferenceInfo info;

        public Change( EntityReferencer.ReferenceInfo info) {
            this.info = info;
        }

        @Override public EntityReferencer.ReferenceInfo getReference()
        {
            return info;
        }
        @Override public String getCurrentId()
        {
            return info.getId();
        }

        @Override public Class<? extends Entity> getType()
        {
            return info.getType();
        }

        public String toString()
        {
        	return "Change " + getCurrentId();//  + " to " + newObj;
        }
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
    

