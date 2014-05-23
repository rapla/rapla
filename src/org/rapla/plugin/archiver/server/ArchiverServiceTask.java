package org.rapla.plugin.archiver.server;

import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.server.ServerExtension;

public class ArchiverServiceTask extends RaplaComponent implements ServerExtension
{
	public ArchiverServiceTask( final RaplaContext context, final Configuration config ) throws RaplaContextException
    {
        super( context );
        final int days = config.getChild( ArchiverService.REMOVE_OLDER_THAN_ENTRY).getValueAsInteger(-20);
        final boolean export = config.getChild( ArchiverService.EXPORT).getValueAsBoolean(false);
        
        if ( days != -20 || export)
        {
            CommandScheduler timer = context.lookup( CommandScheduler.class);
            Command removeTask = new Command() {
            	public void execute() throws RaplaException {
            		ArchiverServiceImpl task = new ArchiverServiceImpl(getContext());
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
			            getLogger().error("Could not execute archiver task ", e);
			        }
            	}
            };
            // Call it each hour
            timer.schedule(removeTask, 0, DateTools.MILLISECONDS_PER_HOUR); 
        }
    }
    

   

}
