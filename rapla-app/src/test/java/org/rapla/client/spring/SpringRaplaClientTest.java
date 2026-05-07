package org.rapla.client.spring;

import org.junit.jupiter.api.Test;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.event.CalendarEventBus;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.plugin.mail.MailToUserInterface;
import org.rapla.storage.RemoteLocaleService;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpringRaplaClientTest
{
    // Re-enabled 2026-05-07 after PRD 002 wiring: CalendarSelectionModel is
    // now @Lazy in ClientConfig, and MultiCalendarPresenter / ConflictSelectionPresenter /
    // RequestSelectionPresenter / ResourceCalendarTask are also @Lazy so they
    // don't trigger CalendarModelImpl construction during context refresh.
    @Test
    void springRaplaClientBoots()
    {
        try (SpringRaplaClient client = new SpringRaplaClient())
        {
            assertNotNull(client.getFacade());
            // Proxies for remote services are wired and ready (they don't actually
            // hit the network until invoked).
            assertNotNull(client.getContext().getBean(ICalTimezones.class));
            assertNotNull(client.getContext().getBean(RemoteLocaleService.class));
            assertNotNull(client.getContext().getBean(MailToUserInterface.class));
            // Phase 2 of PRD 002: RaplaEventBus is the first @Service-annotated Swing class.
            assertNotNull(client.getContext().getBean(ApplicationEventBus.class));
            assertNotNull(client.getContext().getBean(CalendarEventBus.class));
        }
    }
}
