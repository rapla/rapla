package org.rapla.storage.impl.server;

import org.rapla.entities.Entity;

import java.util.Collection;
import java.util.Date;
import java.util.Set;

public interface Changes
{
    Collection<HistoryEntry> getUnresolvedHistoryEntry(String id);
    Collection<String> getAddedIds();
    Collection<String> getRemovedIds();
    Collection<String> getChangedIds();
    HistoryEntry getFirstEntry(String id);
    HistoryEntry getLastEntryBeforeStart(String id);
    HistoryEntry getLastKnown(String id);
    Date getSince();
    Date getUntil();

    Collection<String> getAddedAndChangedIds();

    public class HistoryEntry
    {
        //EntityReferencer.ReferenceInfo info;
        Entity unresolvedEntity;
        Date timestamp;

        public Entity getUnresolvedEntity()
        {
            return unresolvedEntity;
        }

        public Date getTimestamp()
        {
            return timestamp;
        }
    }
}



