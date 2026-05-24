package org.rapla.plugin.archiver.server;

import org.rapla.components.util.DateTools;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.storage.ImportExportManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

/** Hourly task to archive (delete + optionally export) old reservations.
 *
 *  <p>PRD 019 Phase 3c: migrated from {@code ServerExtension} to {@code @Scheduled}.
 *  The hourly cadence is kept; the {@code days != -20 || export} gate is now
 *  evaluated each hour rather than only at start-up, so changes to the system-
 *  preferences config are picked up between hours without a restart. */
public class ArchiverServiceTask
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverServiceTask.class);
    final RaplaFacade facade;
    final org.rapla.storage.SyncStorageOperator syncOperator;
    final ImportExportManager importExportManager;

    @Autowired
    public ArchiverServiceTask(final RaplaFacade facade,
                               final org.rapla.storage.SyncStorageOperator syncOperator,
                               final ImportExportManager importExportManager)
            throws RaplaInitializationException
    {
        this.facade = facade;
        this.syncOperator = syncOperator;
        this.importExportManager = importExportManager;
    }

    @Scheduled(fixedRate = DateTools.MILLISECONDS_PER_HOUR)
    public void runHourlyArchive()
    {
        final RaplaConfiguration config;
        try
        {
            config = facade.getSystemPreferences().getEntry(ArchiverService.CONFIG, new RaplaConfiguration());
        }
        catch (RaplaException e)
        {
            LOGGER.error("Could not read archiver config", e);
            return;
        }
        final int days = config.getChild(ArchiverService.REMOVE_OLDER_THAN_ENTRY).getValueAsInteger(-20);
        final boolean export = config.getChild(ArchiverService.EXPORT).getValueAsBoolean(false);
        if (days == -20 && !export)
        {
            return;
        }
        doArchive(export, days);
    }

    private void doArchive(boolean export, int days)
    {
        try
        {
            if (export && ArchiverServiceImpl.isExportEnabled(facade))
            {
                importExportManager.doExport();
            }
            if (days != -20)
            {
                ArchiverServiceImpl.delete(days, facade, syncOperator);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Could not execute archiver task ", e);
        }
    }
}
