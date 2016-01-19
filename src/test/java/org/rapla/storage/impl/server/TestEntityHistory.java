package org.rapla.storage.impl.server;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.Date;

@RunWith(JUnit4.class)
public class TestEntityHistory
{
    private EntityHistory entityHistory;
    @Before
    public void setUp()
    {
        entityHistory = new EntityHistory();
    }
    
    @Test
    public void deletion()
    {
        final Date timestamp = new Date();
        // insert 10 entries
        ReferenceInfo<Allocatable> ref = new ReferenceInfo<Allocatable>("testId", Allocatable.class);

        for(int i = 0; i < 10; i++)
        {
            String json = null;
            entityHistory.addHistoryEntry(ref, json, new Date(timestamp.getTime() + i), false);
        }
        // remove unneeded for next ms. So no one should be removed
        entityHistory.removeUnneeded(new Date(timestamp.getTime() + 1));
        Assert.assertEquals("" + entityHistory.getHistoryList(ref), 10, entityHistory.getHistoryList(ref).size());
        // now delete all 6 ms in future. as we expect to have one left before, the size must be 5
        entityHistory.removeUnneeded(new Date(timestamp.getTime() + 6));
        Assert.assertEquals("" + entityHistory.getHistoryList(ref), 5, entityHistory.getHistoryList(ref).size());
        // delete all 
        entityHistory.removeUnneeded(new Date(timestamp.getTime() + 50));
        Assert.assertEquals(""+entityHistory.getHistoryList(ref), 1, entityHistory.getHistoryList(ref).size());
    }
    
    @Test
    public void duplicateInsert()
    {
        final Date timestamp = new Date();
        ReferenceInfo<Allocatable> ref = new ReferenceInfo<Allocatable>("test" , Allocatable.class);
        String json = null;
        entityHistory.addHistoryEntry(ref,json, timestamp, false);
        Assert.assertEquals(entityHistory.getHistoryList(ref)+"", 1, entityHistory.getHistoryList(ref).size());
        entityHistory.addHistoryEntry(ref,json, timestamp, false);
        Assert.assertEquals(entityHistory.getHistoryList(ref)+"", 1, entityHistory.getHistoryList(ref).size());
    }
}
