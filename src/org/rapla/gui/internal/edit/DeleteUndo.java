package org.rapla.gui.internal.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class DeleteUndo<T extends Entity<T>> extends RaplaComponent implements CommandUndo<RaplaException> {
	// FIXME Delete of categories in multiple levels can cause the lower levels not to be deleted if it contains categories higher in rank but same hierarchy that are also deleted
	// FIXME Needs a check last changed
	private List<T> removedEntities;
	Map<Category,Category> removedCategories = new LinkedHashMap<Category, Category>();
	public DeleteUndo(RaplaContext context,Collection<T> entities)  
	{
	    super(context);
		this.removedEntities = new ArrayList<T>();
		for ( T entity: entities)
		{
			// Hack for 1.6 compiler compatibility
			if ( ((Object)entity.getRaplaType()) == Category.TYPE)
	    	{
				this.removedEntities.add(entity);
	    	}
			else
			{
				this.removedEntities.add(entity.clone());
			}
		}
	}
	
	public boolean execute() throws RaplaException 
	{
	    Collection<Category> toStore = new ArrayList<Category>();
	    List<T> toRemove = new ArrayList<T>();
	    for ( T entity: removedEntities)
		{
	    	// Hack for 1.6 compiler compatibility
			if ( ((Object)entity.getRaplaType()) == Category.TYPE)
	    	{
				Entity casted = entity;
				// to avoid compiler error
				Category category = (Category) casted;
	    	    Category parent = category.getParent();
	            Category parentClone = null;
	    	    if ( toStore.contains( parent))
	    	    {
	    	    	for ( Category cat: toStore)
	    	    	{
	    	    		if ( cat.equals(parent))
	    	    		{
	    	    			parentClone = parent;
	    	    		}
	    	    	}
	    	    }
	    	    else
	    	    {
		            parentClone = getModification().edit( parent );
		            toStore.add( parentClone);
	    	    }
	    	    if ( parentClone != null)
	    	    {
	    	    	removedCategories.put( category, parent);
	    	    	parentClone.removeCategory( parentClone.findCategory( category));
	    	    }
	    	}
			else
			{
				toRemove.add( entity);
			}
    	}
		Entity<?>[] arrayStore = toStore.toArray( Category.ENTITY_ARRAY);
		@SuppressWarnings("unchecked")
		Entity<T>[] arrayRemove = toRemove.toArray(new Entity[]{});
		getModification().storeAndRemove(arrayStore,arrayRemove);
		return true;
	}
	
	public boolean undo() throws RaplaException 
	{
		List<Entity<T>> toStore = new ArrayList<Entity<T>>();
		for ( T entity: removedEntities)
		{
		    if ( entity instanceof Category)
		    {
		        continue;
		    }
            Entity<T>  mutableEntity = entity.clone();
            // we change the owner of deleted entities because we can't create new objects with owners others than the current user
            if ( mutableEntity instanceof Ownable)
            {
                User user = getUser();
                ((Ownable) mutableEntity).setOwner( user);
            }
			toStore.add( mutableEntity);
		}
		Collection<Category> categoriesToStore2 = new LinkedHashSet<Category>();
		for ( Category category: removedCategories.keySet())
		{
			Category parent = removedCategories.get( category);
			Category parentClone = null;
	 	    if ( categoriesToStore2.contains( parent))
			{
    	    	 for ( Category cat: categoriesToStore2)
    	    	 {
    	    		 if ( cat.equals(parent))
    	    		 {
    	    			 parentClone = parent;
    	    		 }
    	    	 }
    	     }
    	     else
    	     {
	             parentClone = getModification().edit( parent );
		 	     Entity castedParent1 = parentClone;
	             @SuppressWarnings({ "cast", "unchecked" })
	             Entity<T> castedParent = (Entity<T>) castedParent1;
		 	     toStore.add( castedParent);
	             categoriesToStore2.add( parentClone);
    	     }
	 	    if ( parentClone != null)
	 	    {
	 	    	Category clone = category.clone();
                parentClone.addCategory( clone);
	 	    }
		}
		// Todo generate undo for category store
		@SuppressWarnings("unchecked")
        Entity<T>[] array = toStore.toArray(new Entity[]{});
		getModification().storeObjects( array);
		return true;
	}
	
	
	 public String getCommandoName() 
     {
	     Iterator<T> iterator = removedEntities.iterator();
	     StringBuffer buf = new StringBuffer();
	     buf.append(getString("delete") );
	     if ( iterator.hasNext())
	     {
	         RaplaType raplaType = iterator.next().getRaplaType();
	         buf.append( " " +  getString(raplaType.getLocalName()));
	     }
         return buf.toString();
     }   
	    
}