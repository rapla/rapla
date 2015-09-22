package org.rapla.plugin.archiver.server;

import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.server.extensionpoints.ServerExtension;

import javax.inject.Inject;
import javax.inject.Provider;

@Extension(provides = ServerExtension.class,id="archiver")
public class ArchiverServiceTask  implements ServerExtension
{
    @Inject
	public ArchiverServiceTask( final Provider<ArchiverService> archiverProvider, CommandScheduler timer, Logger logger, ClientFacade facade)
            throws RaplaException
    {
        final RaplaConfiguration config = facade.getSystemPreferences().getEntry(ArchiverService.CONFIG,new RaplaConfiguration());
        final int days = config.getChild( ArchiverService.REMOVE_OLDER_THAN_ENTRY).getValueAsInteger(-20);
        final boolean export = config.getChild( ArchiverService.EXPORT).getValueAsBoolean(false);
        if ( days != -20 || export)
        {
            Command removeTask = new Command() {
            	public void execute() throws RaplaException {

            		ArchiverServiceImpl task = (ArchiverServiceImpl)archiverProvider.get();

            		try 
            		{
            			if ( export && task.isExportEnabled())
            			{
            				task.backupNow();
            			}
            			if ( days != -20 )
                        {
                            task.delete( days );
                        }
					} 
            		catch (RaplaException e) {
			            logger.error("Could not execute archiver task ", e);
			        }
            	}
            };
            // Call it each hour
            timer.schedule(removeTask, 0, DateTools.MILLISECONDS_PER_HOUR); 
        }
    }
    

   

}
