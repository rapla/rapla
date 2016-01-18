package org.rapla.plugin.archiver.server;

import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.server.RemoteSession;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbsql.DBOperator;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@DefaultImplementation(of=ArchiverService.class,context= InjectionContext.server)
public class ArchiverServiceImpl  implements ArchiverService
{
    private final RemoteSession session;
    private final RaplaFacade raplaFacade;
    private final ImportExportManager importExportManager;
    private final Logger    logger;

    @Inject
	public ArchiverServiceImpl(
            RaplaFacade facade,
            ImportExportManager importExportManager,
            Logger logger,
            RemoteSession session
            ) {
        this.session = session;
        this.raplaFacade = facade;
        this.importExportManager = importExportManager;
        this.logger = logger;
	}
	
	/** can be overriden to check if user has admin rights when triggered as RemoteService
	 * @throws RaplaException */
	protected void checkAccess() throws RaplaException
	{
        User user = session.getUser();
        if ( user != null && !user.isAdmin())
        {
            throw new RaplaSecurityException("ArchiverService can only be triggered by admin users");
        }
	}
	
	public boolean isExportEnabled() throws RaplaException {
        final RaplaFacade raplaFacade = this.raplaFacade;
        return isExportEnabled(raplaFacade);
	}

    static boolean isExportEnabled(RaplaFacade raplaFacade)
    {
        StorageOperator operator = raplaFacade.getOperator();
        boolean enabled =  operator instanceof DBOperator;
        return enabled;
    }

    public void backupNow() throws RaplaException {
		checkAccess();
		if (!isExportEnabled())
		{
			throw new RaplaException("Export not enabled");
		}

		importExportManager.doExport();
	}

	public void restore() throws RaplaException {
		checkAccess();
		if (!isExportEnabled())
		{
			throw new RaplaException("Export not enabled");
		}
		// We only do an import here 
		importExportManager.doImport();
	}

	public void delete(Integer removeOlderInDays) throws RaplaException {
		checkAccess();
        final RaplaFacade raplaFacade = this.raplaFacade;
        final Logger logger = this.logger;
        delete(removeOlderInDays, raplaFacade, logger);
	}

    static public void delete(Integer removeOlderInDays, RaplaFacade raplaFacade, Logger logger)
    {
        Date endDate = new Date(raplaFacade.today().getTime() - removeOlderInDays * DateTools.MILLISECONDS_PER_DAY);
        Reservation[] events = raplaFacade.getReservations(null, null, endDate, null); //ClassificationFilter.CLASSIFICATIONFILTER_ARRAY );
        List<Reservation> toRemove = new ArrayList<Reservation>();
        for ( int i=0;i< events.length;i++)
        {
            Reservation event = events[i];
            if ( !RaplaComponent.isTemplate(event) && isOlderThan( event, endDate))
            {
                toRemove.add(event);
            }
        }
        if ( toRemove.size() > 0)
        {
            logger.info("Removing " + toRemove.size() + " old events.");
            Reservation[] eventsToRemove = toRemove.toArray( Reservation.RESERVATION_ARRAY);
            int STEP_SIZE = 100;
            for ( int i=0;i< eventsToRemove.length;i+=STEP_SIZE)
            {
                int blockSize = Math.min( eventsToRemove.length- i, STEP_SIZE);
                Reservation[] eventBlock = new Reservation[blockSize];
                System.arraycopy( eventsToRemove,i, eventBlock,0, blockSize);
                raplaFacade.removeObjects(eventBlock);
            }
        }
    }

    static private boolean isOlderThan( Reservation event, Date maxAllowedDate )
	{
        Appointment[] appointments = event.getAppointments();
        for ( int i=0;i<appointments.length;i++)
        {
            Appointment appointment = appointments[i];
            Date start = appointment.getStart();
            Date end = appointment.getMaxEnd();
            if ( start == null || end == null )
            {
                return false;
            }
            if ( end.after( maxAllowedDate))
            {
                return false;
            }
            if ( start.after( maxAllowedDate))
            {
                return false;
            }
        }
        return true;
	}

}