/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.storage;

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ImportExportEntityImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UpdateEvent
{
    transient Map listMap;// = new HashMap<Class, List<Entity>>();
    List<CategoryImpl> categories;
    List<DynamicTypeImpl> types;
    List<UserImpl> users;
    List<PreferencePatch> preferencesPatches;

    List<PreferencesImpl> preferences;
    List<AllocatableImpl> resources;
    List<ReservationImpl> reservations;
    List<ConflictImpl> conflicts;
    List<ImportExportEntityImpl> importExports;

    private Set<SerializableReferenceInfo> removeSet;


    private String userId;

    private boolean needResourcesRefresh = false;

    private TimeInterval invalidateInterval;
    private String lastValidated;
    private int timezoneOffset;

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SerializableReferenceInfo
    {
        String id;
        String localname;

        public SerializableReferenceInfo(ReferenceInfo info)
        {
            this.id = info.getId();
            this.localname = RaplaType.getLocalName(info.getType());
        }

        public SerializableReferenceInfo()
        {
        }

        @Override public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            SerializableReferenceInfo that = (SerializableReferenceInfo) o;

            if (id != null ? !id.equals(that.id) : that.id != null)
                return false;
            return !(localname != null ? !localname.equals(that.localname) : that.localname != null);

        }

        @Override public int hashCode()
        {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (localname != null ? localname.hashCode() : 0);
            return result;
        }

        public ReferenceInfo getReference() throws RaplaException
        {
            final Class<? extends Entity> aClass = RaplaType.find(localname);
            return new ReferenceInfo(id, aClass );
        }
    }

    public UpdateEvent()
    {
    }

    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    public String getUserId()
    {
        return userId;
    }

    //    public Collection<ConflictImpl> getRemoveConflicts() {
    //        if ( removeConflicts == null)
    //        {
    //            return Collections.emptySet();
    //        }
    //        return removeConflicts;
    //    }

    @SuppressWarnings({ "unchecked" }) private Map<Class, List<Entity>> getListMap()
    {
        if (listMap == null)
        {
            listMap = new LinkedHashMap<Class, Collection<Entity>>();
            put(Category.class, categories);
            put(DynamicType.class, types);
            put(Allocatable.class, resources);
            put(User.class, users);
            put(Preferences.class, preferences);
            put(Reservation.class, reservations);
            put(Conflict.class, conflicts);
        }
        return listMap;
    }

    @SuppressWarnings("unchecked") private <T extends Entity> void put(Class<T> class1, List<? extends T> list)
    {
        if (list != null)
        {
            listMap.put(class1, list);
        }

    }

    public void putPatch(PreferencePatch patch)
    {
        if (preferencesPatches == null)
        {
            preferencesPatches = new ArrayList<>();
        }
        preferencesPatches.add(patch);
    }

    public Collection<ReferenceInfo> getRemoveIds() throws RaplaException
    {
        if (removeSet == null)
        {
            return Collections.emptyList();
        }

        Collection<ReferenceInfo> result = new ArrayList<>();
        for (SerializableReferenceInfo entry:removeSet)
        {
            final ReferenceInfo reference = entry.getReference();
            result.add (reference);
        }
        return result;
        //		HashSet<Entity> objects = new LinkedHashSet<Entity>();
        //		for ( Collection<Entity> list:getListMap().values())
        //        {
        //        	for ( Entity entity:list)
        //        	{
        //        		if (  removeSet.contains( entity.getId()))
        //        		{
        //        			objects.add(entity);
        //        		}
        //        	}
        //        }
        //		return objects;

    }

    // Needs to be a linked hashset to keep the order of the entities
    private static final Collector<Entity,?,Collection<Entity>> ENTITY_COLLECTION_COLLECTOR = Collectors.toCollection(LinkedHashSet::new);

    public Collection<Entity> getStoreObjects()
    {
        final Stream<Entity> objectStream = getObjectStream();
        final Collection<Entity> collect = objectStream.collect(ENTITY_COLLECTION_COLLECTOR);
        return collect;

    }

    private Stream<Entity> getObjectStream()
    {
        final Map<Class, List<Entity>> listMap = getListMap();
        return listMap.values().stream().flatMap( Collection::stream);
    }

    public Collection<EntityReferencer> getEntityReferences()
    {
        HashSet<EntityReferencer> objects = new HashSet<>();
        for (Collection<Entity> list : getListMap().values())
        {
            for (Entity entity : list)
            {
                if (entity instanceof EntityReferencer)
                {
                    EntityReferencer references = (EntityReferencer) entity;
                    objects.add(references);
                }
            }
        }

        for (PreferencePatch patch : getPreferencePatches())
        {
            objects.add(patch);
        }
        return objects;

    }

    public List<PreferencePatch> getPreferencePatches()
    {
        if (preferencesPatches == null)
        {
            return Collections.emptyList();
        }
        return preferencesPatches;
    }

    /** adds an entity and doesnt check if its in the lost*/
    public void addStore(Entity entity)
    {
        putStore( entity, false);
    }

    /** adds an entity to the store if it is not present */
    public void addToStoreIfNotExisitant(Entity entity)
    {
        putStore( entity, false);
    }

    private void putStore(Entity entity, boolean checkForDuplicate)
    {
        Class<? extends Entity> class1 = entity.getTypeClass();

        List list = getListMap().get(class1);
        if (list == null)
        {
            if (class1.equals(Reservation.class))
            {
                reservations = new ArrayList<>();
                list = reservations;
            }
            else if (class1.equals(Allocatable.class))
            {
                resources = new ArrayList<>();
                list = resources;
            }
            else if (class1.equals(Preferences.class))
            {
                preferences = new ArrayList<>();
                list = preferences;
            }
            else if (class1.equals(Category.class))
            {
                categories = new ArrayList<>();
                list = categories;
            }
            else if (class1.equals(User.class))
            {
                users = new ArrayList<>();
                list = users;
            }
            else if (class1.equals(DynamicType.class))
            {
                types = new ArrayList<>();
                list = types;
            }
            else if (class1.equals(Conflict.class))
            {
                conflicts = new ArrayList<>();
                list = conflicts;
            }
            else if (class1.equals(ImportExportEntity.class))
            {
                importExports = new ArrayList<>();
                list = importExports;
            }
            else
            {
                throw new IllegalArgumentException(entity.getTypeClass() + " can't be stored ");
            }
            listMap.put(class1, list);
        }
        if ( !checkForDuplicate || !list.contains( entity))
        {
            list.add(entity);
        }
    }

    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putRemove(Entity entity)
    {
        ReferenceInfo id = entity.getReference();
        putRemoveId(id);
    }

    public void putRemoveId(ReferenceInfo ref)
    {
        final SerializableReferenceInfo id = new SerializableReferenceInfo(ref);
        if (removeSet == null)
        {
            removeSet = new LinkedHashSet<>();
            removeSet.add(id);
        }
        else if (!removeSet.contains(id))
        {
            removeSet.add(id);
        }

    }

    /** find an entity in the update-event that matches the passed original. Returns null
     * if no such entity is found. */
    public Entity findEntity(Entity original)
    {
        String originalId = original.getId();
        for (Collection<Entity> list : getListMap().values())
        {
            for (Entity entity : list)
            {
                if (entity.getId().equals(originalId))
                {
                    return entity;
                }
            }
        }
        return null;
    }

    public void setLastValidated(Date serverTime)
    {
        if (serverTime == null)
        {
            this.lastValidated = null;
        }
        this.lastValidated = SerializableDateTimeFormat.INSTANCE.formatTimestamp(serverTime);
    }

    public void setInvalidateInterval(TimeInterval invalidateInterval)
    {
        this.invalidateInterval = invalidateInterval;
    }

    public TimeInterval getInvalidateInterval()
    {
        return invalidateInterval;
    }

    public boolean isNeedResourcesRefresh()
    {
        return needResourcesRefresh;
    }

    public void setNeedResourcesRefresh(boolean needResourcesRefresh)
    {
        this.needResourcesRefresh = needResourcesRefresh;
    }

    //	public Collection<Entity> getAllObjects() {
    //		HashSet<Entity> objects = new HashSet<Entity>();
    //		for ( Collection<Entity> list:getListMap().values())
    //        {
    //        	for ( Entity entity:list)
    //        	{
    //        		objects.add(entity);
    //        	}
    //        }
    //		return objects;
    //	}

    public boolean isEmpty()
    {
        final Map<Class, List<Entity>> listMap = getListMap();
        boolean isEmpty = removeSet == null && listMap.isEmpty() && invalidateInterval == null;
        return isEmpty;
    }

    public Date getLastValidated()
    {
        if (lastValidated == null)
        {
            return null;
        }
        try
        {
            return SerializableDateTimeFormat.INSTANCE.parseTimestamp(lastValidated);
        }
        catch (ParseDateException e)
        {
            throw new IllegalStateException(e.getMessage());
        }
    }

    public int getTimezoneOffset()
    {
        return timezoneOffset;
    }

    public void setTimezoneOffset(int timezoneOffset)
    {
        this.timezoneOffset = timezoneOffset;
    }

    public String getInfoString()
    {
        return getUserId() + " made " + getObjectStream( ).count() + " stores " + ((removeSet!=null)? removeSet.size() : 0) + " removes";
    }

    public String toString()
    {
        return getInfoString();
    }

}
