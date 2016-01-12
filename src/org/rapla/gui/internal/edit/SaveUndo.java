package org.rapla.gui.internal.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.EntityReferencer.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
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
	    if ( !firstTimeCall )
        {
            newEntitiesPersistant = getClientFacade().checklastChanged(newEntities,isNew);
        }
        else
        {
            firstTimeCall = false;
        }
	    for ( T entity: newEntities)
		{
            @SuppressWarnings("unchecked")
			T  mutableEntity = (T) entity.clone();
			if (!isNew)
			{
				@SuppressWarnings("null")
				Entity persistant = (Entity) newEntitiesPersistant.get( entity);
				checkConsistency( mutableEntity );
				setNewTimestamp( mutableEntity, persistant);
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
		    getClientFacade().checklastChanged(newEntities, isNew);
            Entity[] array = newEntities.toArray(new Entity[]{});
			getModification().removeObjects(array);
		} else {
			List<T> toStore = new ArrayList<T>();
			Map<T,T> oldEntitiesPersistant = getClientFacade().checklastChanged(oldEntities, isNew);
			for ( T entity: oldEntities)
    		{
                @SuppressWarnings("unchecked")
				T mutableEntity = (T) entity.clone();
            	T persistantVersion = oldEntitiesPersistant.get( entity);
            	checkConsistency( mutableEntity);
            	setNewTimestamp( mutableEntity, persistantVersion);
				toStore.add( mutableEntity);
    		}
			@SuppressWarnings("unchecked")
            Entity<T>[] array = toStore.toArray(new Entity[]{});
			getModification().storeObjects( array);
		
		}
		return true;
	}
	
	private void checkConsistency(Entity entity) throws EntityNotFoundException {
		// this will also be checked by the server but we try to avoid
		
		if ( entity instanceof SimpleEntity)
		{
			for ( ReferenceInfo info: ((SimpleEntity) entity).getReferenceInfo())
			{
				getClientFacade().getOperator().resolve( info.getId(), info.getType());
			}
		}
		if ( entity instanceof Classifiable)
		{
			Date lastChanged = ((Classifiable) entity).getClassification().getType().getLastChanged();
			if ( lastChanged != null)
			{
				
			}
		}
		
	}
	
	private  void setNewTimestamp( Entity dest, Entity persistant) {
		 if ( persistant instanceof ModifiableTimestamp)
		 {
			 Date version = ((ModifiableTimestamp)persistant).getLastChanged();
			 ((ModifiableTimestamp)dest).setLastChanged(version);
		 }
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