package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SaveUndo<T extends Entity> implements CommandUndo<RaplaException> {
	final Map<T,T> storeListCopy;
	final private Map<T,Boolean> isNew;
	protected final String commandoName;
   	private final RaplaFacade facade;
   	RaplaResources i18n;
   	boolean firstTime = true;


	public SaveUndo(RaplaFacade facade, RaplaResources i18n,Map<T,T> storeList)
	{
		this( facade, i18n,storeList, null);
	}

	public SaveUndo(RaplaFacade facade, RaplaResources i18n,Map<T,T> storeList, String commandoName)
	{
	    this.facade = facade;
	    this.i18n = i18n;
	    this.commandoName = commandoName;
	    storeListCopy = new LinkedHashMap<>();
	    isNew = new LinkedHashMap<>();
	    storeList.entrySet().stream().forEach((entry)->
				{
					final T key = (T)entry.getKey().clone();
					final T value = (T) entry.getValue().clone();
					final boolean isNew = !entry.getKey().isReadOnly();
					this.isNew.put (key, isNew);
					storeListCopy.put(key, value);
				}

		);
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
		final Set<T> oldEntities = storeListCopy.keySet();
 		final Collection<T> newEntities = storeListCopy.values();

		final RaplaFacade facade = getFacade();
		if ( firstTime)
		{
			return facade.dispatch( newEntities ,Collections.emptyList());
		}
		return facade.editListAsync(oldEntities).thenCompose( (newEntitiespersistent)->
			{
				List<T> toStore = new ArrayList<>();
				for (T entity : newEntities) {
					@SuppressWarnings("unchecked") T mutableEntity = (T) entity.clone();
					if (newEntitiespersistent != null) {
						@SuppressWarnings("null")
						Entity persistent = newEntitiespersistent.get(entity);
						try {
							checkConsistency(mutableEntity);
						} catch (EntityNotFoundException ex) {
							return new ResolvedPromise<>(ex);
						}
						setNewTimestamp(mutableEntity, persistent);
					}
					toStore.add(mutableEntity);
				}
				return this.facade.dispatch(toStore, Collections.emptyList());
			}).thenRun(()->firstTime = false);

	}

	public Promise<Void> undo()  {

		final Set<T> oldEntities = storeListCopy.keySet();
		return getFacade().editListAsyncForUndo(oldEntities).thenCompose(map ->
					{
						List<T> toStore = new ArrayList<>();
						List<ReferenceInfo<T>> toRemove = new ArrayList<>();
						Map<T, T> oldEntitiespersistent = map;
						for (T entity : oldEntities)
						{
							if ( !isNew.get( entity)) {
								@SuppressWarnings("unchecked") T mutableEntity = (T) entity.clone();
								T persistentVersion = oldEntitiespersistent.get(entity);
								checkConsistency(mutableEntity);
								setNewTimestamp(mutableEntity, persistentVersion);
								toStore.add(mutableEntity);
							}
							else
							{
								toRemove.add( entity.getReference());
							}
						}
						return getFacade().dispatch(toStore, toRemove);
					}
			);
	}

	private void checkConsistency(Entity entity) throws EntityNotFoundException {
		// this will also be checked by the server but we try to avoid
		
		if ( entity instanceof SimpleEntity)
		{
			for ( ReferenceInfo info: ((SimpleEntity) entity).getReferenceInfo())
			{
				if ( info.getType() != User.class) {
					getFacade().resolve(info);
				}
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
	
	private  void setNewTimestamp( Entity dest, Entity persistent) {
		 if ( persistent instanceof ModifiableTimestamp)
		 {
			 Date version = ((ModifiableTimestamp)persistent).getLastChanged();
			 ((ModifiableTimestamp)dest).setLastChanged(version);
		 }
	 }

	 public String getCommandoName()
     {
		 if ( commandoName != null)
		 {
			 return commandoName;
		 }
         Iterator<T> iterator = storeListCopy.values().iterator();
         StringBuffer buf = new StringBuffer();
         boolean isNew = this.isNew.values().stream().allMatch(Boolean::booleanValue);
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