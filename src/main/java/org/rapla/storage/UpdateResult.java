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
import java.util.Map;
import java.util.Set;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.storage.EntityReferencer;

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
        /*
        RaplaType raplaType = operation.getRaplaType();
        if ( raplaType != null)
        {
        	modified.add( raplaType);
        }
        if(operation instanceof Remove){
            // FIXME
        }
        */
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
    
    public HistoryEntry getLastEntryBeforeUpdate(String id)
    {
        // FIXME remove history or add timestamp
        // TODO Conflict resolution
        final Entity entity = oldEntities.get(id);
        final HistoryEntry historyEntry = new HistoryEntry();
        historyEntry.unresolvedEntity = entity;
        return historyEntry;
    }

    public Entity getLastKnown(String id)
    {
        // TODO Conflict resolution
        return updatedEntities.get( id );
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
    

