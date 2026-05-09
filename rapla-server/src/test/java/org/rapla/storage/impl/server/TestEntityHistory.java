package org.rapla.storage.impl.server;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PermissionController;

import java.time.LocalDateTime;
@RunWith(JUnit4.class)
public class TestEntityHistory
{
    private EntityHistory entityHistory;
    @Before
    public void setUp()
    {
        EntityResolver resolver = null;
        entityHistory = new EntityHistory(resolver);
    }
    
    @Test
    public void deletion()
    {
        final LocalDateTime timestamp = LocalDateTime.now();
        // insert 10 entries
        ReferenceInfo<Allocatable> ref = new ReferenceInfo<Allocatable>("testId", Allocatable.class);

        for(int i = 0; i < 10; i++)
        {
            String json = null;
            entityHistory.addHistoryEntry(ref, json, DateTools.toLocalDateTime(DateTools.toMilli(timestamp) + i), false);
        }
        // remove unneeded for next ms. So no one should be removed
        entityHistory.removeUnneeded(DateTools.toLocalDateTime(DateTools.toMilli(timestamp) + 1));
        Assert.assertEquals("" + entityHistory.getHistoryList(ref), 10, entityHistory.getHistoryList(ref).size());
        // now delete all 6 ms in future. as we expect to have one left before, the size must be 5
        entityHistory.removeUnneeded(DateTools.toLocalDateTime(DateTools.toMilli(timestamp) + 6));
        Assert.assertEquals("" + entityHistory.getHistoryList(ref), 5, entityHistory.getHistoryList(ref).size());
        // delete all 
        entityHistory.removeUnneeded(DateTools.toLocalDateTime(DateTools.toMilli(timestamp) + 50));
        Assert.assertEquals(""+entityHistory.getHistoryList(ref), 1, entityHistory.getHistoryList(ref).size());
    }
    
    @Test
    public void duplicateInsert()
    {
        final LocalDateTime timestamp = LocalDateTime.now();
        ReferenceInfo<Allocatable> ref = new ReferenceInfo<Allocatable>("test" , Allocatable.class);
        String json = null;
        entityHistory.addHistoryEntry(ref,json, timestamp, false);
        Assert.assertEquals(entityHistory.getHistoryList(ref)+"", 1, entityHistory.getHistoryList(ref).size());
        entityHistory.addHistoryEntry(ref,json, timestamp, false);
        Assert.assertEquals(entityHistory.getHistoryList(ref)+"", 1, entityHistory.getHistoryList(ref).size());
    }
}
