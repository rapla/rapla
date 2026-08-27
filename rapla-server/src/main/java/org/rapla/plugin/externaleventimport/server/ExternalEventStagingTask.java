package org.rapla.plugin.externaleventimport.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/** Drives the staging reconciliation: on the configured cadence, and immediately at startup.
 *
 *  <p>The startup run bypasses the freshness window — a restart is exactly when current data is
 *  wanted — but still respects the running flag in the meta row, so a rolling restart of N pods
 *  never overlaps. */
public class ExternalEventStagingTask
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalEventStagingTask.class);

    private final ExternalEventStagingService stagingService;

    public ExternalEventStagingTask(ExternalEventStagingService stagingService)
    {
        this.stagingService = stagingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup()
    {
        LOGGER.info("Running external event staging reconciliation at startup.");
        log(stagingService.reconcile(true));
    }

    @Scheduled(cron = "${rapla.externalevents.staging.cron:0 0/20 * * * *}", zone = "Europe/Berlin")
    public void runScheduled()
    {
        log(stagingService.reconcile(false));
    }

    private void log(StagingReconcileResult result)
    {
        if (result.skipped())
        {
            LOGGER.debug("Staging reconciliation skipped: " + result.skipReason());
        }
        // Failures are logged once by the service — no second line here.
    }
}
