package org.rapla.storage.impl.server;

import java.util.Date;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.domain.Allocatable;

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
        final String key = "testId";
        for(int i = 0; i < 10; i++)
        {
            entityHistory.addHistoryEntry(key, "test" + i, Allocatable.class, new Date(timestamp.getTime() + i), false);
        }
        // remove unneeded for next ms. So no one should be removed
        entityHistory.removeUnneeded(new Date(timestamp.getTime() + 1));
        Assert.assertEquals("" + entityHistory.getHistoryList(key), 10, entityHistory.getHistoryList(key).size());
        // now delete all 6 ms in future. as we expect to have one left before, the size must be 5
        entityHistory.removeUnneeded(new Date(timestamp.getTime() + 6));
        Assert.assertEquals("" + entityHistory.getHistoryList(key), 5, entityHistory.getHistoryList(key).size());
        // delete all 
        entityHistory.removeUnneeded(new Date(timestamp.getTime() + 50));
        Assert.assertEquals(""+entityHistory.getHistoryList(key), 1, entityHistory.getHistoryList(key).size());
    }
    
    @Test
    public void duplicateInsert()
    {
        final Date timestamp = new Date();
        final String key = "test";
        entityHistory.addHistoryEntry(key, "test", Allocatable.class, timestamp, false);
        Assert.assertEquals(entityHistory.getHistoryList(key)+"", 1, entityHistory.getHistoryList(key).size());
        entityHistory.addHistoryEntry(key, "test", Allocatable.class, timestamp, false);
        Assert.assertEquals(entityHistory.getHistoryList(key)+"", 1, entityHistory.getHistoryList(key).size());
    }
}
