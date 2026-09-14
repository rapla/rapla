package org.rapla.plugin.exchangeconnector.server;

import org.rapla.components.util.DateTools;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * PRD 070 — carries the Exchange-sync {@code @Scheduled} triggers, split off
 * {@link SynchronisationManager} so scheduling is gated per deployment. Registered as a
 * {@code @Bean} with {@code @ConditionalOnProperty("rapla.exchange.enabled")}
 * in {@code ServerServiceConfig}: the bean (and thus all scheduling) exists only on the
 * deployment that enables it (the dhbw sync pod), while {@code SynchronisationManager}
 * itself stays available everywhere for the GUI/connect endpoints.
 *
 * <p>The {@code @Scheduled} cadences mirror the legacy periods that previously lived on
 * the manager's two sweep methods.
 */
public class ExchangeSchedulerTrigger
{
    /** 6 s — short queue sweep cadence (was {@code SynchronisationManager.SCHEDULE_PERIOD}). */
    private static final long SCHEDULE_PERIOD = DateTools.MILLISECONDS_PER_MINUTE / 10;
    /** 60 min — mailbox refresh sweep cadence. */
    private static final long SCHEDULE_PERIOD_REFRESH_MAILBOXES = DateTools.MILLISECONDS_PER_MINUTE * 60;

    private final SynchronisationManager manager;

    public ExchangeSchedulerTrigger(SynchronisationManager manager)
    {
        this.manager = manager;
    }

    @Scheduled(fixedRate = SCHEDULE_PERIOD)
    public void synchronizeQueue()
    {
        manager.synchronizeQueue();
    }

    @Scheduled(fixedRate = SCHEDULE_PERIOD_REFRESH_MAILBOXES)
    public void synchronizeMailboxes()
    {
        manager.synchronizeMailboxes();
    }
}
