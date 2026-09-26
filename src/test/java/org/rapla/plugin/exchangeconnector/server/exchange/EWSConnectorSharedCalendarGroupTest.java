package org.rapla.plugin.exchangeconnector.server.exchange;

import org.junit.Assert;
import org.junit.Test;

public class EWSConnectorSharedCalendarGroupTest {

    @Test
    public void acceptsLegacyGermanGroupName() {
        Assert.assertTrue(EWSConnector.isSharedCalendarGroup("Weitere Kalender"));
    }

    @Test
    public void acceptsNewOutlookGermanGroupName() {
        Assert.assertTrue(EWSConnector.isSharedCalendarGroup("Andere Kalender"));
    }

    @Test
    public void acceptsEnglishGroupName() {
        Assert.assertTrue(EWSConnector.isSharedCalendarGroup("Other Calendars"));
    }

    @Test
    public void rejectsOwnCalendarsAndMissingGroup() {
        Assert.assertFalse(EWSConnector.isSharedCalendarGroup("Meine Kalender"));
        Assert.assertFalse(EWSConnector.isSharedCalendarGroup(null));
    }
}
