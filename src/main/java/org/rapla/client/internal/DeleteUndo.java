package org.rapla.client.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.rapla.RaplaResources;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;

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
	
	public Promise<Void> execute()
	{
	    Collection<Entity<?>> toStore = Collections.emptyList();
	    Collection<ReferenceInfo<T>> toRemove = new ArrayList<>();
	    for ( T entity: entities)
		{
            toRemove.add( entity.getReference());
    	}
		return getFacade().dispatch(toStore,toRemove, user);
	}
	
	public Promise<Void> undo()
	{
		Collection<Entity<?>> toStore = new ArrayList<>();
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
		Collection<ReferenceInfo<Entity<?>>> toRemove = Collections.emptyList();
		return getFacade().dispatch(toStore,toRemove, user);
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