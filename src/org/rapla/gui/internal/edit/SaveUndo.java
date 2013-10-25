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
import org.rapla.entities.storage.Mementable;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class SaveUndo<T extends Entity<T>> extends RaplaComponent implements CommandUndo<RaplaException> {
	protected final List<Entity<T>> newEntities;
	protected final List<Entity<T>> oldEntities;
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
	    this.newEntities = new ArrayList<Entity<T>>();
		for ( T entity: newEntity)
		{
			@SuppressWarnings("unchecked")
            Mementable<T> mementable = (Mementable<T>)entity;
            this.newEntities.add(mementable.deepClone());
		}
		if (originalEntity !=null)
		{
			if ( originalEntity.size() != newEntity.size() )
			{
				throw new IllegalArgumentException("Original and new list need the same size");
			}
			this.oldEntities = new ArrayList<Entity<T>>();
			for ( T entity: originalEntity)
    		{
				@SuppressWarnings("unchecked")
                Mementable<T> mementable = (Mementable<T>)entity;
                this.oldEntities.add( mementable.deepClone());
    		}
		}
		else
		{
			this.oldEntities = null;
		}
	}
	
	public boolean execute() throws RaplaException {
		final boolean isNew = oldEntities == null;

		List<Entity<T>> toStore = new ArrayList<Entity<T>>();
		Map<Entity<T>,T> newEntitiesPersistant = null;
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
		for ( Entity<T> entity: newEntities)
		{
			RefEntity<T> newEntity = (RefEntity<T>) entity;
			@SuppressWarnings("unchecked")
            Mementable<T> mementable = (Mementable<T>)entity;
            RefEntity<T>  mutableEntity = (RefEntity<T>) mementable.deepClone();
			if (!isNew)
			{
				@SuppressWarnings("null")
				RefEntity<T> persistant = (RefEntity<T>) newEntitiesPersistant.get( entity);
				copyVersions(newEntity, mutableEntity, persistant);
			}
			toStore.add( mutableEntity);
		}
		@SuppressWarnings("unchecked")
        Entity<T>[] array = toStore.toArray(new Entity[]{});
		getModification().storeObjects( array);
		return true;
	}

	protected void checklastChanged(List<Entity<T>> entities, Map<Entity<T>,T> persistantVersions) throws RaplaException,
			EntityNotFoundException {
		getUpdateModule().refresh();
		for ( Entity<T> entity:entities)
		{
			if ( entity instanceof ModifiableTimestamp)
			{
				Entity<T> persistant = persistantVersions.get( entity);
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
			Map<Entity<T>,T> newEntitiesPersistant = getModification().getPersistant(newEntities);
			checklastChanged(newEntities, newEntitiesPersistant);
			@SuppressWarnings("unchecked")
            Entity<T>[] array = newEntities.toArray(new Entity[]{});
			getModification().removeObjects(array);
		} else {
			List<Entity<T>> toStore = new ArrayList<Entity<T>>();
			int i=0;
			Map<Entity<T>,T> oldEntitiesPersistant = getModification().getPersistant(oldEntities);
			checklastChanged(oldEntities, oldEntitiesPersistant);
			for ( Entity<T> entity: oldEntities)
    		{
				@SuppressWarnings("unchecked")
                Mementable<T> mementable = (Mementable<T>)entity;
                RefEntity<T> mutableEntity = (RefEntity<T>) mementable.deepClone();
            	RefEntity<T> persistantVersion = (RefEntity<T>) oldEntitiesPersistant.get( entity);
            	RefEntity<T> versionedEntity = (RefEntity<T>) newEntities.get( i);
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
	 private  void copyVersions(RefEntity<T> source, RefEntity<T> dest, RefEntity<T> persistant) {
		for (RefEntity<?> next:source.getSubEntities())
		{
			RefEntity<?> foundEntity = persistant.findEntity( next);
			if ( foundEntity != null)
			{
				long version = foundEntity.getVersion();
				RefEntity<?> refEntity = (RefEntity<?>) dest.findEntity(next);
				if ( refEntity != null)
				{
					refEntity.setVersion(version);
				}
			}
		}
		
		long version = persistant.getVersion();
		dest.setVersion(version);
	 }
	 
	 public String getCommandoName() 
     {
		 if ( commandoName != null)
		 {
			 return commandoName;
		 }
	     boolean isNew = oldEntities == null;     
         Iterator<Entity<T>> iterator = newEntities.iterator();
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