package org.rapla.plugin.archiver.server;

import io.reactivex.disposables.Disposable;
import org.rapla.components.util.DateTools;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.storage.ImportExportManager;

import javax.inject.Inject;

@Extension(provides = ServerExtension.class,id="org.rapla.plugin.archiver.server")
public class ArchiverServiceTask  implements ServerExtension
{
    final CommandScheduler timer;
    final Logger logger;
    final RaplaFacade facade;
    final ImportExportManager importExportManager;
    Disposable schedule;
    @Inject
	public ArchiverServiceTask(  CommandScheduler timer, final Logger logger, final RaplaFacade facade, final ImportExportManager importExportManager)
            throws RaplaInitializationException
    {

        this.timer = timer;
        this.logger = logger;
        this.facade = facade;
        this.importExportManager =importExportManager;
    }

    @Override public void start()
    {

        final RaplaConfiguration config;
        try
        {
            config = facade.getSystemPreferences().getEntry(ArchiverService.CONFIG,new RaplaConfiguration());
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        final int days = config.getChild( ArchiverService.REMOVE_OLDER_THAN_ENTRY).getValueAsInteger(-20);
        final boolean export = config.getChild( ArchiverService.EXPORT).getValueAsBoolean(false);
        if ( days != -20 || export)
        {
            // Call it each hour
            schedule = timer.schedule(() -> doArchive(export, days), 0, DateTools.MILLISECONDS_PER_HOUR);
        }
    }
    
    public void stop()
    {
        if ( schedule != null)
        {
            schedule.dispose();
        }
    }

    private void doArchive(boolean export, int days)
    {
        try
        {
            if ( export && ArchiverServiceImpl.isExportEnabled(facade))
            {
                importExportManager.doExport();
            }
            if ( days != -20 )
            {
                ArchiverServiceImpl.delete(days,facade,logger);
            }
        }
        catch (Exception e) {
            logger.error("Could not execute archiver task ", e);
        }
    }
}
