package org.rapla.storage.impl.server;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.Category;
import org.rapla.entities.Timestamp;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.logger.ConsoleLogger;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PermissionController;

import java.time.Instant;
import java.util.Date;

@RunWith(JUnit4.class)
public class TestEntityHistory
{
    private EntityHistory entityHistory;
    @Before
    public void setUp()
    {
        EntityResolver resolver = null;
        entityHistory = new EntityHistory(resolver, new ConsoleLogger());
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

    /** Rapla 3 writes LocalDateTime with 1-9 fractional digits and no Z. restinject's
     *  ISODateTimeFormat reads the fraction as a plain millisecond integer, so ".12"
     *  became 12ms instead of 120ms and nanoseconds shifted the date by days. */
    @Test
    public void raplaThreeFractionalSecondsAreReadAsFraction()
    {
        String json = "{\"annotations\":{},\"createDate\":\"2014-08-15T15:37:58.12\",\"id\":\"c1\","
                + "\"key\":\"department\",\"lastChanged\":\"2026-09-27T01:23:22.686712205\","
                + "\"name\":{\"mapLocales\":{\"en\":\"department\"}}}";
        ReferenceInfo<Category> ref = new ReferenceInfo<Category>("c1", Category.class);
        final EntityHistory.HistoryEntry entry = entityHistory.addHistoryEntry(ref, json, new Date(), false);

        final Timestamp timestamp = (Timestamp) entityHistory.getEntity(entry);
        Assert.assertEquals(Date.from(Instant.parse("2014-08-15T15:37:58.120Z")), timestamp.getCreateDate());
        Assert.assertEquals(Date.from(Instant.parse("2026-09-27T01:23:22.686Z")), timestamp.getLastChanged());
    }

    /** Exactly three digits, with and without Z, must pass through untouched. */
    @Test
    public void threeDigitFractionIsUnchanged()
    {
        String json = "{\"annotations\":{},\"createDate\":\"2014-08-15T15:37:58.123Z\",\"id\":\"c2\","
                + "\"key\":\"department\",\"lastChanged\":\"2026-09-27T01:23:22.456\","
                + "\"name\":{\"mapLocales\":{\"en\":\"department\"}}}";
        ReferenceInfo<Category> ref = new ReferenceInfo<Category>("c2", Category.class);
        final EntityHistory.HistoryEntry entry = entityHistory.addHistoryEntry(ref, json, new Date(), false);

        final Timestamp timestamp = (Timestamp) entityHistory.getEntity(entry);
        Assert.assertEquals(Date.from(Instant.parse("2014-08-15T15:37:58.123Z")), timestamp.getCreateDate());
        Assert.assertEquals(Date.from(Instant.parse("2026-09-27T01:23:22.456Z")), timestamp.getLastChanged());
    }

    /** A single unparseable entry must not propagate: tryGetEntity logs and returns null
     *  so start-up, refresh and client updates skip it instead of dying. */
    @Test
    public void unparseableEntryIsSkipped()
    {
        ReferenceInfo<Category> ref = new ReferenceInfo<Category>("broken", Category.class);
        final EntityHistory.HistoryEntry entry = entityHistory.addHistoryEntry(ref, "{kaputt", new Date(), false);

        try
        {
            entityHistory.getEntity(entry);
            Assert.fail("getEntity should propagate the parse error");
        }
        catch (Exception expected)
        {
        }
        Assert.assertNull(entityHistory.tryGetEntity(entry));
    }
}
