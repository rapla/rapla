package org.rapla.client.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;

public class SaveUndo<T extends Entity> implements CommandUndo<RaplaException> {
	protected final List<T> newEntities;
	protected final List<T> oldEntities;
	protected final String commandoName;
   	protected boolean firstTimeCall = true;
   	private RaplaFacade facade;
   	RaplaResources i18n;

	public SaveUndo(RaplaFacade facade, RaplaResources i18n,Collection<T> newEntity,Collection<T> originalEntity)
	{
		this( facade, i18n,newEntity, originalEntity, null);
	}

	public SaveUndo(RaplaFacade facade, RaplaResources i18n,Collection<T> newEntity,Collection<T> originalEntity, String commandoName)
	{
	    this.facade = facade;
	    this.i18n = i18n;
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

	protected RaplaFacade getFacade()
    {
        return facade;
    }
	
	protected I18nBundle getI18n()
	{
	    return i18n;
	}
	
	protected Locale getLocale()
	{
	    return getI18n().getLocale();
	}
	
	public Promise<Void> execute() {
		CommandScheduler scheduler =facade.getScheduler();
		Promise<Void> promise = scheduler.run(() -> {
			final boolean isNew = oldEntities == null;

			List<T> toStore = new ArrayList<T>();
			Map<T, T> newEntitiesPersistant = null;
			// undo
			if (!firstTimeCall)
			{
				newEntitiesPersistant = getFacade().checklastChanged(newEntities, isNew);
			}
			else
			{
				// FIXME CHECK EVENTS
				//checkEvents(newEntitiesPersistant.values(), popupContext);
				firstTimeCall = false;
			}
			for (T entity : newEntities)
			{
				@SuppressWarnings("unchecked") T mutableEntity = (T) entity.clone();
				if (newEntitiesPersistant != null)
				{
					@SuppressWarnings("null") Entity persistant = newEntitiesPersistant.get(entity);
					checkConsistency(mutableEntity);
					setNewTimestamp(mutableEntity, persistant);
				}
				toStore.add(mutableEntity);
			}
			@SuppressWarnings("unchecked") Entity<T>[] array = toStore.toArray(new Entity[] {});

			facade.storeObjects(array);
		});
		return promise;
	}

	public Promise<Void> undo()  {
		CommandScheduler scheduler =facade.getScheduler();
		Promise<Void> promise = scheduler.run(() -> {

			boolean isNew = oldEntities == null;

			if (isNew)
			{
				getFacade().checklastChanged(newEntities, isNew);
				Entity[] array = newEntities.toArray(new Entity[] {});
				getFacade().removeObjects(array);
			}
			else
			{
				List<T> toStore = new ArrayList<T>();
				Map<T, T> oldEntitiesPersistant = getFacade().checklastChanged(oldEntities, isNew);
				for (T entity : oldEntities)
				{
					@SuppressWarnings("unchecked") T mutableEntity = (T) entity.clone();
					T persistantVersion = oldEntitiesPersistant.get(entity);
					checkConsistency(mutableEntity);
					setNewTimestamp(mutableEntity, persistantVersion);
					toStore.add(mutableEntity);
				}
				@SuppressWarnings("unchecked") Entity<T>[] array = toStore.toArray(new Entity[] {});
				getFacade().storeObjects(array);

			}
		});
		return promise;
	}
	
	private void checkConsistency(Entity entity) throws EntityNotFoundException {
		// this will also be checked by the server but we try to avoid
		
		if ( entity instanceof SimpleEntity)
		{
			for ( ReferenceInfo info: ((SimpleEntity) entity).getReferenceInfo())
			{
				getFacade().resolve( info );
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
         buf.append(isNew ? i18n.getString("new"): i18n.getString("edit") );
         if ( iterator.hasNext())
         {
			 final Entity next = iterator.next();
			 final String localName = RaplaType.getLocalName(next);
			 buf.append(" " + i18n.getString(localName));
         }
         return buf.toString();
     }



}