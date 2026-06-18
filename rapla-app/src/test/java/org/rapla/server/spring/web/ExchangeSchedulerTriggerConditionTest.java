package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.plugin.exchangeconnector.server.ExchangeSchedulerTrigger;
import org.rapla.plugin.exchangeconnector.server.SynchronisationManager;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 070 — the deployment split. {@link ExchangeSchedulerTrigger} (the {@code @Scheduled}
 * sweeps) must exist only where {@code rapla.exchange.enabled=true} (the dhbw sync pod),
 * while {@link SynchronisationManager} (the service backing the GUI/connect endpoints) is
 * present on every deployment regardless.
 */
class ExchangeSchedulerTriggerConditionTest
{
    @SpringBootTest(classes = RaplaSpringBootApplication.class, properties = "rapla.exchange.enabled=true")
    static class WhenEnabled
    {
        @Autowired
        ApplicationContext ctx;

        @Test
        void triggerPresentAndManagerAvailable()
        {
            assertEquals(1, ctx.getBeanNamesForType(ExchangeSchedulerTrigger.class).length,
                    "trigger bean must exist when rapla.exchange.enabled=true");
            assertEquals(1, ctx.getBeanNamesForType(SynchronisationManager.class).length,
                    "manager service is present regardless");
        }
    }

    @SpringBootTest(classes = RaplaSpringBootApplication.class, properties = "rapla.exchange.enabled=false")
    static class WhenDisabled
    {
        @Autowired
        ApplicationContext ctx;

        @Test
        void triggerAbsentButManagerStillAvailable()
        {
            assertEquals(0, ctx.getBeanNamesForType(ExchangeSchedulerTrigger.class).length,
                    "no trigger bean (no scheduling) when rapla.exchange.enabled=false");
            assertEquals(1, ctx.getBeanNamesForType(SynchronisationManager.class).length,
                    "manager service stays available so the GUI/connect endpoints still work");
        }
    }
}
