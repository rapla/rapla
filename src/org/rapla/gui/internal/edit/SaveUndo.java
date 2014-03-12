package org.rapla.gui.internal.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class SaveUndo<T extends Entity> extends RaplaComponent implements CommandUndo<RaplaException> {
	protected final List<T> newEntities;
	protected final List<T> oldEntities;
	protected final String commandoName;
   	protected boolean firstTimeCall = true;
	
	public SaveUndo(RaplaContext context,Collection<T> newEntity,Collection<T> originalEntity) 
	{
		this( context, newEntity, originalEntity, null);
	}
	public SaveUndo(RaplaContext context,Collection<T> newEntity,Collection<T> originalEntity, String commandoName) 
	{
	    super(context);
	    this.commandoName = commandoName;
	    this.newEntities = new ArrayList<T>();
		for ( T entity: newEntity)
		{
            @SuppressWarnings("unchecked")
			T clone = (T) entity.clone();
			this.newEntities.add(clone);
		}
		if (originalEntity !=null)
		{
			if ( originalEntity.size() != newEntity.size() )
			{
				throw new IllegalArgumentException("Original and new list need the same size");
			}
			this.oldEntities = new ArrayList<T>();
			for ( T entity: originalEntity)
    		{
                @SuppressWarnings("unchecked")
				T clone = (T) entity.clone();
                this.oldEntities.add( clone);
    		}
		}
		else
		{
			this.oldEntities = null;
		}
	}
	
	public boolean execute() throws RaplaException {
		final boolean isNew = oldEntities == null;

		List<T> toStore = new ArrayList<T>();
		Map<T,T> newEntitiesPersistant = null;
		if ( !firstTimeCall || !isNew)
		{
			newEntitiesPersistant= getModification().getPersistant(newEntities);
		}
		if ( firstTimeCall)
		{
			firstTimeCall = false;
		}
		else
		{
			checklastChanged(newEntities, newEntitiesPersistant);
		}
		for ( T entity: newEntities)
		{
			Entity newEntity =  entity;
            @SuppressWarnings("unchecked")
			T  mutableEntity = (T) entity.clone();
			if (!isNew)
			{
				@SuppressWarnings("null")
				Entity persistant = (Entity) newEntitiesPersistant.get( entity);
				copyVersions(newEntity, mutableEntity, persistant);
			}
			toStore.add( mutableEntity);
		}
		@SuppressWarnings("unchecked")
        Entity<T>[] array = toStore.toArray(new Entity[]{});
		getModification().storeObjects( array);
		return true;
	}

	protected void checklastChanged(List<T> entities, Map<T,T> persistantVersions) throws RaplaException,
			EntityNotFoundException {
		getUpdateModule().refresh();
		for ( T entity:entities)
		{
			if ( entity instanceof ModifiableTimestamp)
			{
				T persistant = persistantVersions.get( entity);
				if ( persistant != null) 
				{
					User lastChangedBy = ((ModifiableTimestamp) persistant).getLastChangedBy();
					if (lastChangedBy != null && !getUser().equals(lastChangedBy))
					{
						String name = entity instanceof Named ? ((Named) entity).getName( getLocale()) : entity.toString();
						throw new RaplaException(getI18n().format("error.new_version", name));
					}		
				} 
				else
				{
					// if there exists an older version
					if ( oldEntities != null)
					{
						String name = entity instanceof Named ? ((Named) entity).getName( getLocale()) : entity.toString();
						throw new RaplaException(getI18n().format("error.new_version", name));
					}
					// otherwise we ignore it
				}
			
			}
		}
	}
	
	public boolean undo() throws RaplaException {
		boolean isNew = oldEntities == null;

		if (isNew) {
			Map<T,T> newEntitiesPersistant = getModification().getPersistant(newEntities);
			checklastChanged(newEntities, newEntitiesPersistant);
            Entity[] array = newEntities.toArray(new Entity[]{});
			getModification().removeObjects(array);
		} else {
			List<T> toStore = new ArrayList<T>();
			int i=0;
			Map<T,T> oldEntitiesPersistant = getModification().getPersistant(oldEntities);
			checklastChanged(oldEntities, oldEntitiesPersistant);
			for ( T entity: oldEntities)
    		{
                @SuppressWarnings("unchecked")
				T mutableEntity = (T) entity.clone();
            	T persistantVersion = oldEntitiesPersistant.get( entity);
            	T versionedEntity = newEntities.get( i);
				copyVersions(versionedEntity, mutableEntity, persistantVersion);
				toStore.add( mutableEntity);
    		}
			@SuppressWarnings("unchecked")
            Entity<T>[] array = toStore.toArray(new Entity[]{});
			getModification().storeObjects( array);
		
		}
		return true;
	}
	
	// FIXME this method is dangerous, because dynamic type changes can be overwritten.
	 private  void copyVersions(Entity source, Entity dest, Entity persistant) {
		if ( source instanceof ParentEntity)
		{
			for (Entity next:((ParentEntity)source).getSubEntities())
			{
				RefEntity foundEntity = (RefEntity) ((ParentEntity)persistant).findEntity( next);
				if ( foundEntity != null)
				{
					int version = foundEntity.getVersion();
					RefEntity refEntity = (RefEntity) ((ParentEntity)dest).findEntity(next);
					if ( refEntity != null)
					{
						refEntity.setVersion(version);
					}
				}
			}
		}
		
		int version = ((RefEntity)persistant).getVersion();
		((RefEntity)dest).setVersion(version);
	 }
	 
	 public String getCommandoName() 
     {
		 if ( commandoName != null)
		 {
			 return commandoName;
		 }
	     boolean isNew = oldEntities == null;     
         Iterator<T> iterator = newEntities.iterator();
         StringBuffer buf = new StringBuffer();
         buf.append(isNew ? getString("new"): getString("edit") );
         if ( iterator.hasNext())
         {
             RaplaType raplaType = iterator.next().getRaplaType();
             buf.append(" " + getString(raplaType.getLocalName()));
         }
         return buf.toString();
     }   
	    
}