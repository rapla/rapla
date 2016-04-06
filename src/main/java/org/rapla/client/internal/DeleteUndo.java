package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DeleteUndo<T extends Entity<T>>  implements CommandUndo<RaplaException> {
	// FIXME Delete of categories in multiple levels can cause the lower levels not to be deleted if it contains categories higher in rank but same hierarchy that are also deleted
	// FIXME Needs a check last changed
	private Set<T> entities;
	RaplaFacade facade;
	RaplaResources i18n;
	User user;
	public DeleteUndo( RaplaFacade facade,RaplaResources i18n,Collection<T> entities, User user)
	{
	    this.facade = facade;
	    this.i18n = i18n;
		this.user = user;
		this.entities = new LinkedHashSet<T>();
		for ( T entity: entities)
		{
			this.entities.add(entity.clone());
			if ( entity.getTypeClass() == Category.class)
	    	{
				final Collection<Category> recursive = CategoryImpl.getRecursive((Category) entity);
				for ( Category category: recursive)
				{
					this.entities.add((T) category);
				}
	    	}
		}
	}
	
	public RaplaFacade getFacade()
    {
        return facade;
    }
	public I18nBundle getI18n()
    {
        return i18n;
    }
	
	public boolean execute() throws RaplaException 
	{
	    Collection<Category> toStore = new ArrayList<Category>();
	    List<T> toRemove = new ArrayList<T>();
	    for ( T entity: entities)
		{
			toRemove.add( entity);
    	}
		Entity<?>[] arrayStore = toStore.toArray( Category.ENTITY_ARRAY);
		@SuppressWarnings("unchecked")
		Entity<T>[] arrayRemove = toRemove.toArray(new Entity[]{});
		getFacade().storeAndRemove(arrayStore,arrayRemove);
		return true;
	}
	
	public boolean undo() throws RaplaException 
	{
		List<Entity<T>> toStore = new ArrayList<Entity<T>>();
		for ( T entity: entities)
		{
            Entity<T>  mutableEntity = entity.clone();
            // we change the owner of deleted entities because we can't create new objects with owners others than the current user
            if ( mutableEntity instanceof Ownable)
            {
                ((Ownable) mutableEntity).setOwner( user);
            }
			toStore.add( mutableEntity);
		}
		// Todo generate undo for category store
		@SuppressWarnings("unchecked")
        Entity<T>[] array = toStore.toArray(new Entity[]{});
		getFacade().storeObjects( array);
		return true;
	}
	
	
	 public String getCommandoName() 
     {
	     Iterator<T> iterator = entities.iterator();
	     StringBuffer buf = new StringBuffer();
	     buf.append(i18n.getString("delete") );
	     if ( iterator.hasNext())
	     {
			 String localname = RaplaType.getLocalName(iterator.next());
	         buf.append( " " +  getI18n().getString(localname));
	     }
         return buf.toString();
     }   
	    
}