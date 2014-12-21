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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import org.rapla.components.util.Assert;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.RefEntity;

/** Base-class for all Rapla Entity-Implementations. Provides services
 * for deep cloning and serialization of references. {@link ReferenceHandler}
*/

public abstract class SimpleEntity extends ReferenceHandler implements RefEntity, Comparable
{
    private String id;
    transient boolean readOnly = false;
    
    public SimpleEntity() {
    }
    
    public void checkWritable() {
        if ( readOnly )
            throw new ReadOnlyException( this );
    }
    
    @Deprecated
    public boolean isPersistant() {
        return isReadOnly();
    }

    public void setResolver( EntityResolver resolver)  {
        super.setResolver( resolver);
        Iterable<Entity>subEntities = getSubEntities();
		for (Entity subEntity :subEntities)
        {
        	((EntityReferencer)subEntity).setResolver( resolver );
        }
    }
    
    public boolean isIdentical(Entity object)
    {
    	return equals( object);
    }
    
    private Iterable<Entity>getSubEntities() {
		if  (!( this instanceof ParentEntity))
		{
			return Collections.emptyList();
		}
		else
		{
			//@SuppressWarnings("unchecked")
			Iterable<Entity>subEntities = ((ParentEntity)this).getSubEntities();
			return subEntities;
		}
	}

	public void setReadOnly() {
        this.readOnly = true;
        for (Entity ref:getSubEntities()) {
            ((SimpleEntity)ref).setReadOnly();
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public User getOwner() {
        return getEntity("owner", User.class);
    }
    
    public String getOwnerId()
    {
        return getId("owner");
    }

    public void setOwner(User owner) {
        putEntity("owner",owner);
    }
    
	public User getLastChangedBy() {
		return getEntity("last_changed_by", User.class);
	}

	public void setLastChangedBy(User user) {
        putEntity("last_changed_by",user);
	}
	
	@Override
	protected Class<? extends Entity> getInfoClass(String key) {
	    if ( key.equals( "owner") || key.equals("last_changed_by"))
	    {
	        return User.class;
	    }
	    if ( key.equals("person"))
	    {
	        return Allocatable.class;
	    }
	    return null;
	}

   
    /** sets the identifier for an object. The identifier should be
     * unique accross all entities (not only accross the entities of a
     * the same type). Once set, the identifier for an object should
     * not change. The identifier is necessary to store the relationsships
     * between enties.
     * @see SimpleIdentifier
     */
    public void setId(String id)  {
    	if ( id != null)
    	{
    		id = id.intern();
    	}
        this.id= id;
    }

    /** @return the identifier of the object.
     * @see SimpleIdentifier
     */
    final public String getId()  {
        return id;
    }
    
    /** two Entities are equal if they are identical.
     * @see #isIdentical
     */
    final public boolean equals(Object o) {
        if (!( o instanceof Entity))
        {
            return false;
        }
        Entity e2 = (Entity) o;
        String id2 = e2.getId(); 
        if ( id2== null || id == null)
            return e2 == this;
        if (id == id2)
        {
            return true;
        }
        return id.equals(id2);
    }
    
    /** The hashcode of the id-object will be returned.
     * @return the hashcode of the id.
     * @throws IllegalStateException if no id is set.
    */
    public int hashCode() {
        if ( id != null) {
            return id.hashCode();
        } else {
            //super.hashCode();
            throw new IllegalStateException("Id not set. You must set an Id before you can use the hashCode method." );
        }
    }

    /** find the sub-entity that has the same id as the passed copy. Returns null, if the entity was not found. */
	public Entity findEntity(Entity copy) {
        for (Entity entity:getSubEntities())
        {
            if (entity.equals(copy)) {
                return entity;
            }
        }
        return null;
    }

    /** find the sub-entity that has the same id as the passed copy. Returns null, if the entity was not found. */
	public Entity findEntityForId(String id) {
        for (Entity entity:getSubEntities())
        {
            if (id.equals(entity.getId())) {
                return entity;
            }
        }
        return null;
    }


    protected void deepClone(SimpleEntity clone) {
    	clone.id = id;
    	clone.links =  new LinkedHashMap<String,List<String>>();
    	for ( String key:links.keySet())
    	{
    		List<String> idList = links.get( key);
    		clone.links.put( key, new ArrayList<String>(idList));
    	}
    	clone.resolver = this.resolver;
    	Assert.isTrue(!clone.getSubEntities().iterator().hasNext());
    	ArrayList<Entity>newSubEntities = new ArrayList<Entity>();
    	Iterable<Entity> oldEntities = getSubEntities();
    	for (Entity entity: oldEntities)
    	{
    		Entity deepClone = (Entity) entity.clone();
    		newSubEntities.add( deepClone);
    	}
    	for (Entity entity: newSubEntities)
    	{
    		((ParentEntity)clone).addEntity( entity );
    	}
    }

    public String toString() {
        if (id != null)
            return id.toString();
        return "no id for " + super.toString();
    }

	public int compareTo(Object o) 
    {
    	return compare_(this, (RefEntity)o);
    }
	
	static public <T extends Entity> void checkResolveResult(String id, Class<T> entityClass, T entity) throws EntityNotFoundException {
        if ( entity == null)
        {
            Object serializable = entityClass != null ? entityClass : "Object";
            throw new EntityNotFoundException(serializable +" for id [" + id + "] not found for class ", id);
        }
    }

    static protected int compare_(RefEntity o1,RefEntity o2) {
        if ( o1 == o2)
        {
            return 0;
        }
        if ( o1.equals( o2))
            return 0;
 
        // first try to compare the entities with their create time
        if ( o1 instanceof Timestamp && o2 instanceof Timestamp)
        {
        	Date c1 = ((Timestamp)o1).getCreateTime();
           	Date c2 = ((Timestamp)o2).getCreateTime();
           	if ( c1 != null && c2 != null)
           	{
           		int result = c1.compareTo( c2);
           		if ( result != 0)
           		{
           			return result;
           		}
           	}
        }
        String id1 = o1.getId();
        String id2 = o2.getId();
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
    }

    public static int timestampCompare(Timestamp t1, Timestamp t2) {
        if ( t1 == t2 || t1.equals( t2) )
        {
            return 0;
        }
        Date d1 = t1.getCreateTime();
        Date d2 = t2.getCreateTime();
        if ( d1 == null && d2 == null)
        {
            return 0;
        }
        if ( d1 == null)
        {
            return 1;
        }
        if ( d2 == null)
        {
            return -1;
        }
        if ( d1.before( d2))
        {
            return -1;
        }
        if ( d1.after( d2))
        {
            return 1;
        }
        return 0;
        
    }

}




