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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.User;
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
    private int version = 0;
    //transient protected ReferenceHandler subEntityHandler;

    transient boolean readOnly = false;
    transient Integer idKey;
    
    public SimpleEntity() {

    }
    
    public void checkWritable() {
        if ( readOnly )
            throw new ReadOnlyException( this );
    }
    
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

	public void setReadOnly(boolean enable) {
        this.readOnly = enable;
        for (Entity ref:getSubEntities()) {
            ((SimpleEntity)ref).setReadOnly(enable);
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public User getOwner() {
        return (User) getEntity("owner");
    }

    public void setOwner(User owner) {
        putEntity("owner",owner);
    }
    
	public User getLastChangedBy() {
		return (User) getEntity("last_changed_by");
	}

	public void setLastChangedBy(User user) {
        putEntity("last_changed_by",user);
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
        idKey = null;
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
        Object id2 = e2.getId(); 
        if ( id2== null || id == null)
            return e2 == this;
        return id.equals( id2);
    }
    
    public Integer getIdKey()
    {
    	if ( idKey == null && id != null)
    	{
    		idKey = ((RaplaObject)this).getRaplaType().getKey( id );
    	}
    	return idKey;
    }

    /** The hashcode of the id-object will be returned.
     * @return the hashcode of the id.
     * @throws IllegalStateException if no id is set.
    */
    public int hashCode() {
        if ( id != null) {
            return id.hashCode();
        } else {
            throw new IllegalStateException("Id not set. You must set an Id before you can use the hashCode method."
                                            );
        }
    }

    public void setVersion(int version)  {
        this.version= version;
    }

    public int getVersion()  {
        return version;
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


    @SuppressWarnings("unchecked")
	protected void deepClone(SimpleEntity dest) {
    	dest.id = id;
    	dest.links =  (Map<String, List<String>>) ((HashMap)links).clone();
    	dest.resolver = this.resolver;
    	ArrayList<Entity>newSubEntities = new ArrayList<Entity>();
    	for (Entity entity: getSubEntities())
    	{
    		Entity deepClone = (Entity) entity.clone();
    		newSubEntities.add( deepClone);
    	}
    	for (Entity entity: newSubEntities)
    	{
    		((ParentEntity)dest).addEntity( entity );
    	}
    	// In a copy operation the target/destination object should always be writable
    	dest.readOnly = false;
    	dest.setVersion(getVersion());
    }

//    @SuppressWarnings("unchecked")
//	public T cast()
//    {
//    	return (T) this;
//    }

    public String toString() {
        if (id != null)
            return id.toString();
        return "no id for " + super.toString();
    }

	public int compareTo(Object o) 
    {
    	return compare_(this, (SimpleEntity)o);
    }
	
	static private int compare_(SimpleEntity o1,SimpleEntity o2) {
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




