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
import org.rapla.entities.storage.ReferenceInfo;

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
    private final List<UpdateOperation> operations = new ArrayList<>();
	//Set<RaplaType> modified = new HashSet<RaplaType>();
	private final Date since;
	private final Date until;
    private final Map<ReferenceInfo, Entity> oldEntities;
    private final Map<ReferenceInfo, Entity> updatedEntities;

    public UpdateResult(Date since, Date until, Map<ReferenceInfo, Entity> oldEntities,Map<ReferenceInfo, Entity> updatedEntities)
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
        
        List<T> list = new ArrayList<>();
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

    /** returns null if no entity exisits before update*/
    public <T extends Entity> T getLastEntryBeforeUpdate(ReferenceInfo<T> id)
    {
        final T entity = (T) oldEntities.get(id);
        return entity;
    }

    public <T extends Entity> T getLastKnown(ReferenceInfo<T> id)
    {
        return (T) updatedEntities.get( id );
    }

    public void addOperation(Entity<?> newEntity, Entity<?> oldEntity, UpdateOperation operation)
    {
        addOperation(operation);
        if (oldEntity !=null) {
            oldEntities.put(oldEntity.getReference(), oldEntity);
        }
        if (newEntity !=null) {
            updatedEntities.put(newEntity.getReference(), newEntity);
        }
    }

    static public class Add implements UpdateOperation {
        ReferenceInfo info;

        public Add( ReferenceInfo info) {
            this.info = info;
        }

        @Override public ReferenceInfo getReference()
        {
            return info;
        }
        
        public String toString()
        {
        	return "Add " + info;
        }

        @Override public Class<? extends Entity> getType()
        {
            return info.getType();
        }
    }

    static public class Remove implements UpdateOperation {
        ReferenceInfo info;

        public Remove(ReferenceInfo info) {
            this.info = info;
        }

        @Override public ReferenceInfo getReference()
        {
            return info;
        }

        @Override public Class<? extends Entity> getType()
        {
            return info.getType();
        }

        public String toString()
        {
        	return "Remove " + info;
        }

    }

    static public class Change implements UpdateOperation{
        
        ReferenceInfo info;

        public Change( ReferenceInfo info) {
            this.info = info;
        }

        @Override public ReferenceInfo getReference()
        {
            return info;
        }

        @Override public Class<? extends Entity> getType()
        {
            return info.getType();
        }

        public String toString()
        {
        	return "Change " + info;//  + " to " + newObj;
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


    public Collection<ReferenceInfo> getAddedAndChangedIds()
    {
        Set<ReferenceInfo> result = new LinkedHashSet<>();
        fillIds(result, Add.class);
        fillIds(result, Change.class);
        return result;
    }

    public Collection<ReferenceInfo> getRemovedIds()
    {
        Set<ReferenceInfo> result = new LinkedHashSet<>();
        fillIds(result, Remove.class);
        return result;
    }

    private <T extends UpdateOperation> void fillIds(Set<ReferenceInfo> result, final Class<T> operationClass)
    {
        final Collection<T> operations = getOperations(operationClass);
        for (UpdateOperation operation : operations)
        {
            result.add(operation.getReference());
        }
    }

    public <T extends UpdateOperation> Collection<ReferenceInfo> getIds(final Class<T> operationClass)
    {
        final LinkedHashSet<ReferenceInfo> result = new LinkedHashSet<>();
        fillIds(result, operationClass);
        return result;
    }

}
    

