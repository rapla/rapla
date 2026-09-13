package org.rapla.plugin.exchangeconnector.server.exchange;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EWSConnectorSharedCalendarGroupTest {

    @Test
    void acceptsLegacyGermanGroupName() {
        assertTrue(EWSConnector.isSharedCalendarGroup("Weitere Kalender"));
    }

    @Test
    void acceptsNewOutlookGermanGroupName() {
        assertTrue(EWSConnector.isSharedCalendarGroup("Andere Kalender"));
    }

    @Test
    void acceptsEnglishGroupName() {
        assertTrue(EWSConnector.isSharedCalendarGroup("Other Calendars"));
    }

    @Test
    void rejectsOwnCalendarsAndMissingGroup() {
        assertFalse(EWSConnector.isSharedCalendarGroup("Meine Kalender"));
        assertFalse(EWSConnector.isSharedCalendarGroup(null));
    }
}
