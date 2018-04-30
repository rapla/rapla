package org.rapla.storage.impl.server;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.rest.JsonParserWrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityHistory
{
    public Collection<ReferenceInfo> getAllIds()
    {
        return map.keySet();
    }

    public static boolean isSupportedEntity(Class<? extends Entity> type)
    {
        return type == Allocatable.class || type == DynamicType.class || type == Reservation.class || type == User.class || type == Category.class
                || type == Conflict.class;
    }

    public static class HistoryEntry
    {
        private long timestamp;
        ReferenceInfo ref;
        private String json;
        private boolean isDelete;

        private HistoryEntry()
        {
        }

        private HistoryEntry(ReferenceInfo id, long timestamp, String json, boolean isDelete)
        {
            super();
            this.ref = id;
            this.isDelete = isDelete;
            this.timestamp = timestamp;
            this.json = json;
        }

        public ReferenceInfo getId()
        {
            return ref;
        }

        public long getTimestamp()
        {
            return timestamp;
        }

        public boolean isDelete()
        {
            return isDelete;
        }

        @Override public String toString()
        {
            return "HistoryEntry [timestamp=" + timestamp + ", id=" + ref + "]";
        }
    }

    private final Map<ReferenceInfo, List<EntityHistory.HistoryEntry>> map = new LinkedHashMap<>();
    private final JsonParserWrapper.JsonParser gson;

    public EntityHistory()
    {
        gson = JsonParserWrapper.defaultJson().get();
    }

    public HistoryEntry getLatest(ReferenceInfo id) throws RaplaException
    {
        final List<HistoryEntry> historyEntries = map.get(id);
        if (historyEntries == null || historyEntries.isEmpty())
        {
            throw new RaplaException("History not available for id " + id);
        }
        return historyEntries.get(historyEntries.size() - 1);
    }

    public boolean hasHistory(ReferenceInfo id)
    {
        final boolean result = map.get(id) != null;
        return result;
    }

    /** returns the history entry with a timestamp<= since or null if no such entry exists*/
    public Entity get(ReferenceInfo id, Date since) throws RaplaException
    {
        final List<EntityHistory.HistoryEntry> historyEntries = map.get(id);
        if (historyEntries == null)
        {
            throw new RaplaException("History not available for id " + id);
        }
        final EntityHistory.HistoryEntry emptyEntryWithTimestamp = new EntityHistory.HistoryEntry();
        emptyEntryWithTimestamp.timestamp = since.getTime();
        int index = Collections.binarySearch(historyEntries, emptyEntryWithTimestamp, (o1, o2) -> (int) (o1.timestamp - o2.timestamp));
        /*
        * possible results:
        * we get an index >= 0 -> We found an entry, which has the timestamp of the last update from the client. We need to get this one
        * we get an index < 0 -> We have no entry within the list, which has the timestamp. Corresponding to the binary search API -index -1 is the index where to insert a entry having this timestamp. So we need -index -1 to get the last one with an timestamp smaller than the requested one.
        */
        if (index < 0)
        {
            index = -index - 2;
        }
        if (index < 0 && !historyEntries.isEmpty())
        {
            EntityHistory.HistoryEntry entry = historyEntries.get(0);
            final Date lastChanged = getLastChanged(entry);
            if ( lastChanged.before( since))
            {
                return getEntity( entry);
            }
            else
                {
                return null;
            }
        }
        EntityHistory.HistoryEntry entry = historyEntries.get(index);
        final Entity entity = getEntity(entry);
        if ( index >=0)
        {
            // if two history entries have the same timestamp
            // then check the timestamp on the entities and return
            EntityHistory.HistoryEntry entryBefore = null;
            if ( index > 0)
            {
                entryBefore = historyEntries.get(index - 1);
            }
            if (index + 1< historyEntries.size() && (entryBefore == null || entryBefore.getTimestamp() != entry.getTimestamp() ))
            {
                entryBefore = historyEntries.get(index + 1);
            }
            if (entryBefore != null && entryBefore.getTimestamp() == entry.getTimestamp())
            {
                Entity otherEntity = getEntity( entryBefore);
                final Date lastChanged1 = ((Timestamp) entity).getLastChanged();
                final Date lastChanged2= ((Timestamp) (otherEntity)).getLastChanged();
                // we return the newest change
                if ( lastChanged2.after( lastChanged1))
                {
                    return otherEntity;
                }
            }
        }

        return entity;
    }

    Map<Class<? extends Entity>, Class<? extends Entity>> typeImpl = new HashMap<>();

    {
        addMap(Reservation.class, ReservationImpl.class);
        //addMap(Appointment.class, AppointmentImpl.class);
        addMap(Allocatable.class, AllocatableImpl.class);
        addMap(Category.class, CategoryImpl.class);
        addMap(User.class, UserImpl.class);
        addMap(Conflict.class, ConflictImpl.class);
        addMap(DynamicType.class, DynamicTypeImpl.class);

    }

    <T extends Entity> void addMap(Class<T> type, Class<? extends T> impl)
    {
        typeImpl.put(type, impl);
    }

    public Entity getEntity(HistoryEntry entry)
    {
        String json = entry.json;
        final Class typeClass = entry.getId().getType();
        final Class<? extends Entity> implementingClass = typeImpl.get(typeClass);
        final Entity entity = gson.fromJson(json, implementingClass);
        return entity;
    }

    public EntityHistory.HistoryEntry addHistoryEntry(ReferenceInfo id, String json, Date timestamp, boolean isDelete)
    {
        List<EntityHistory.HistoryEntry> historyEntries = map.get(id);
        if (historyEntries == null)
        {
            historyEntries = new ArrayList<>();
            map.put(id, historyEntries);
        }
        final EntityHistory.HistoryEntry newEntry = new EntityHistory.HistoryEntry(id, timestamp.getTime(), json, isDelete);
        int index = historyEntries.size();
        insert(historyEntries, newEntry, index);
        return newEntry;
    }

    private void insert(List<EntityHistory.HistoryEntry> historyEntries, EntityHistory.HistoryEntry newEntry, int index)
    {
        if (index == 0)
        {
            historyEntries.add(0, newEntry);
        }
        else
        {
            final HistoryEntry lastEntry = historyEntries.get(index - 1);
            final long timestamp = lastEntry.timestamp;
            if (timestamp > newEntry.timestamp)
            {
                insert(historyEntries, newEntry, index - 1);
            }
            else if (timestamp == newEntry.timestamp)
            {
                final String json = newEntry.json;
                if (json != null && !json.equals( lastEntry.json))
                {
                    Date lastChanged1 = getLastChanged(newEntry);
                    Date lastChanged2 = getLastChanged(lastEntry);
                    if ( lastChanged1.before(lastChanged2))
                    {
                        historyEntries.add(index-1, newEntry);
                    }
                    else
                    {
                        historyEntries.add(index, newEntry);
                    }
                }
            }
            else
            {
                historyEntries.add(index, newEntry);
            }
        }
    }

    private Date getLastChanged(HistoryEntry newEntry)
    {
        final Entity entity = getEntity(newEntry);
        return((Timestamp)entity).getLastChanged();
    }

    public EntityHistory.HistoryEntry addHistoryEntry(Entity entity, Date timestamp, boolean isDelete)
    {
        final ReferenceInfo id = entity.getReference();
        final String json = gson.toJson(entity);
        return addHistoryEntry(id, json, timestamp, isDelete);
    }

    public EntityHistory.HistoryEntry addHistoryDeleteEntry(Entity entity, Date timestamp, boolean isDelete)
    {
        final ReferenceInfo id = entity.getReference();
        final String json = gson.toJson(entity);
        return addHistoryEntry(id, json,  timestamp, isDelete);
    }

    public void clear()
    {
        map.clear();
    }

    List<HistoryEntry> getHistoryList(ReferenceInfo key)
    {
        return map.get(key);
    }

    public void removeUnneeded(Date date)
    {
        final Set<ReferenceInfo> keySet = map.keySet();
        final long time = date.getTime();
        for (ReferenceInfo key : keySet)
        {
            final List<HistoryEntry> list = map.get(key);
            while (list.size() >= 2 && list.get(1).timestamp < time)
            {
                list.remove(0);
            }
        }
    }

    /**
     * Returns the entity with the given id where timestamp >= last_changed from the entity.
     * @param id
     * @param timestamp
     * @return
     */
    public HistoryEntry getLastChangedUntil(ReferenceInfo id, Date timestamp)
    {
        final long time = timestamp.getTime();
        final List<HistoryEntry> list = map.get(id);
        for (int i = list.size() - 1; i >= 0; i--)
        {
            final HistoryEntry historyEntry = list.get(i);
            if (historyEntry.getTimestamp() <= time)
            {
                return historyEntry;
            }
        }
        return null;
    }
}