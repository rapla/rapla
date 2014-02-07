/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Gereon Fassbender, Christopher Kohlhaas               |
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
package org.rapla.entities.storage.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.rapla.components.util.Assert;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.User;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.Mementable;
import org.rapla.entities.storage.RefEntity;

/** Base-class for all Rapla Entity-Implementations. Provides services
 * for deep cloning and serialization of references. {@link ReferenceHandler}
*/

public abstract class SimpleEntity<T> implements RefEntity<T>,Comparable<T>
{
    private String id;
    private long version = 0;

    protected ReferenceHandler subEntityHandler;
    ReferenceHandler referenceHandler = new ReferenceHandler();

    transient boolean readOnly = false;
    transient Integer key;
    
    public SimpleEntity() {

    }

    public void checkWritable() {
        if ( readOnly )
            throw new ReadOnlyException( this );
    }
    
    public boolean isPersistant() {
        return isReadOnly();
    }

    public void resolveEntities( EntityResolver resolver) throws EntityNotFoundException {
        referenceHandler.resolveEntities( resolver);
        if ( subEntityHandler != null)
        {
        	subEntityHandler.resolveEntities( resolver );
        }
    }
    
    public void setReadOnly(boolean enable) {
        this.readOnly = enable;
        for (RefEntity<?> ref:getSubEntities()) {
            ((SimpleEntity<?>)ref).setReadOnly(enable);
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public User getOwner() {
        return (User) referenceHandler.get("owner");
    }

    @SuppressWarnings("unchecked")
	public void setOwner(User owner) {
        referenceHandler.put("owner",(RefEntity<User>)owner);
    }
    
	public User getLastChangedBy() {
		return (User) referenceHandler.get("last_changed_by");
	}

	@SuppressWarnings("unchecked")
	public void setLastChangedBy(User user) {
        referenceHandler.put("last_changed_by",(RefEntity<User>)user);
	}


    protected boolean isSubEntity(RefEntity<?> obj) {
    	if ( subEntityHandler == null)
    	{
    		return false;
    	}
        return subEntityHandler.isRefering(obj);
    }

    protected void addEntity(RefEntity<?> entity) {
    	throw new UnsupportedOperationException();
    }

    public ReferenceHandler getReferenceHandler() {
        return referenceHandler;
    }

    protected ReferenceHandler getSubEntityHandler() {
        return subEntityHandler;
    }

    protected void removeEntity(RefEntity<?> entity) {
    	if ( subEntityHandler != null)
    	{
    		subEntityHandler.isRefering(entity);
    		subEntityHandler.remove(entity);
    	}
    }


    /** sets the identifier for an object. The identifier should be
     * unique accross all entities (not only accross the entities of a
     * the same type). Once set, the identifier for an object should
     * not change. The identifier is necessary to store the relationsships
     * between enties.
     * @see SimpleIdentifier
     */
    public void setId(String id)  {
        this.id= id;
        key = null;
    }

    /** @return the identifier of the object.
     * @see SimpleIdentifier
     */
    final public String getId()  {
        return id;
    }
    
    final public boolean isIdentical(Entity<?> ob2) {
        return equals( ob2);
    }

    /** two Entities are equal if they are identical.
     * @see #isIdentical
     */
    final public boolean equals(Object o) {
        if (!( o instanceof SimpleEntity))
        {
            return false;
        }
        @SuppressWarnings("rawtypes")
		SimpleEntity e2 = (SimpleEntity) o;
        Object id2 = e2.id; 
        if ( id2== null || id == null)
            return e2 == this;
        return id.equals( id2);

    }
    
    public Integer getIdKey()
    {
    	if ( key == null && id != null)
    	{
    		key = getRaplaType().getKey( id );
    	}
    	return key;
    }

    /** The hashcode of the id-object will be returned.
     * @return the hashcode of the id.
     * @throws IllegalStateException if no id is set.
    */
    public int hashCode() {
        if ( id != null) {
            return id.hashCode();
        } else {
            throw new IllegalStateException("Id not set for type '" +  getRaplaType()
                                            + "'. You must set an Id before you can use the hashCode method."
                                            );
        }
    }

    public void setVersion(long version)  {
        this.version= version;
    }

    public long getVersion()  {
        return version;
    }

    public Iterable<RefEntity<?>> getSubEntities() {
    	if ( subEntityHandler != null)
    	{
            return subEntityHandler.getReferences();
    	}
    	Set<RefEntity<?>> emptySet = Collections.emptySet();
		return emptySet;
    }

    public Iterable<RefEntity<?>> getReferences() {
        return getReferenceHandler().getReferences();
    }

    public boolean isRefering(RefEntity<?> entity) {
        ReferenceHandler referenceHandler = getReferenceHandler();
        return referenceHandler.isRefering(entity);
    }

    public boolean isParentEntity(RefEntity<?> object) {
    	if ( subEntityHandler == null)
    	{
    		return false;
    	}
        return subEntityHandler.isRefering(object);
    }
    
	@SuppressWarnings("unchecked")
	static private <T> void copy(SimpleEntity<T> source,SimpleEntity<T> dest,boolean deepCopy) {
        Assert.isTrue(source != dest,"can't copy the same object");

        dest.referenceHandler = (ReferenceHandler) source.referenceHandler.clone();

        ArrayList<RefEntity<?>> newSubEntities = new ArrayList<RefEntity<?>>();
        for (RefEntity<?> entity: source.getSubEntities())
        {
            RefEntity<?> oldEntity = dest.findEntity(entity);
            if (oldEntity != null) {
                if ( deepCopy && oldEntity != entity)
                {
                    ((Mementable<RefEntity<?>>)oldEntity).copy(entity);
                }
                newSubEntities.add( oldEntity);
            } else {
                if ( deepCopy)
                {
                    RefEntity<?> deepClone = ((Mementable<RefEntity<?>>)entity).deepClone();
                    newSubEntities.add( deepClone);
                }
                else
                {
                    newSubEntities.add( entity);
                }
            }
        }
        if ( dest.subEntityHandler != null)
        {
        	dest.subEntityHandler.clearReferences();
        }
        for (RefEntity<?> entity: newSubEntities)
        {
            dest.addEntity( entity );
        }
        // In a copy operation the target/destination object should always be writable
        dest.readOnly = false;
        dest.setVersion(source.getVersion());
    }

    /** find the sub-entity that has the same id as the passed copy. Returns null, if the entity was not found. */
	public RefEntity<?> findEntity(RefEntity<?> copy) {
        for (RefEntity<?> entity:getSubEntities())
        {
            if (entity.equals(copy)) {
                return entity;
            }
        }
        return null;
    }

    /** copies the references from the entity to this */
    protected void copy(SimpleEntity<T> entity) {
    	synchronized ( this) {
            copy(entity,this,true);
		}
    }

    protected void deepClone(SimpleEntity<T> clone) {
        clone.setId(id);
        copy(this,clone,true);
    }

    @SuppressWarnings("unchecked")
	public T cast()
    {
    	return (T) this;
    }

    public String toString() {
        if (id != null)
            return id.toString();
        return "no id for " + super.toString();
    }

	public int compareTo(T o) 
    {
    	return compare_(this, (SimpleEntity<?>)o);
    }
	
	static private int compare_(SimpleEntity<?> o1,SimpleEntity<?> o2) {
        if ( o1 == o2)
        {
            return 0;
        }
        Integer id1 = o1.getIdKey();
        Integer id2 = o2.getIdKey();
        if ( o1.equals( o2))
            return 0;
        if ( id1 == null)
        {
       	 if ( id2 == null)
       	 {
       		throw new IllegalStateException("Can't compare two entities without ids");
       	 }
       	 else
       	 {
       		return -1; 
       	 }
        }
        else if ( id2 == null)
        {
       	 	return 1;
        }
        return id1.compareTo( id2 );
        //Integer key1 = o1.getRaplaType().getKey( id1 );
        //Integer key2  = o2.getRaplaType().getKey( id2 );
        //return key1.compareTo( key2);        
    }

}




