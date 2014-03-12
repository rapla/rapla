package org.rapla.plugin.archiver.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbsql.DBOperator;

public class ArchiverServiceImpl extends RaplaComponent implements ArchiverService
{
	public ArchiverServiceImpl(RaplaContext context) {
		super(context);
	}
	
	/** can be overriden to check if user has admin rights when triggered as RemoteService
	 * @throws RaplaException */
	protected void checkAccess() throws RaplaException
	{
	}
	
	public boolean isExportEnabled() throws RaplaException {
		ClientFacade clientFacade = getContext().lookup(ClientFacade.class);
		StorageOperator operator = clientFacade.getOperator();
		boolean enabled =  operator instanceof DBOperator;
		return enabled;
	}

	public void backupNow() throws RaplaException {
		checkAccess();
		if (!isExportEnabled())
		{
			throw new RaplaException("Export not enabled");
		}
		ImportExportManager lookup = getContext().lookup(ImportExportManager.class);
		lookup.doExport();
	}

	public void restore() throws RaplaException {
		checkAccess();
		if (!isExportEnabled())
		{
			throw new RaplaException("Export not enabled");
		}
		// We only do an import here 
		ImportExportManager lookup = getContext().lookup(ImportExportManager.class);
		lookup.doImport();
	}

	public void delete(Integer removeOlderInDays) throws RaplaException {
		checkAccess();
		ClientFacade clientFacade = getContext().lookup(ClientFacade.class);
		Date endDate = new Date(clientFacade.today().getTime() - removeOlderInDays * DateTools.MILLISECONDS_PER_DAY);
        Reservation[] events = clientFacade.getReservations((User) null, null, endDate, null); //ClassificationFilter.CLASSIFICATIONFILTER_ARRAY );
        List<Reservation> toRemove = new ArrayList<Reservation>();
        for ( int i=0;i< events.length;i++)
        {
            Reservation event = events[i];
            if ( isOlderThan( event, endDate))
            {
                toRemove.add(event);
            }
        }
        if ( toRemove.size() > 0)
        {
            getLogger().info("Removing " + toRemove.size() + " old events.");
            Reservation[] eventsToRemove = toRemove.toArray( Reservation.RESERVATION_ARRAY);
            int STEP_SIZE = 100;
            for ( int i=0;i< eventsToRemove.length;i+=STEP_SIZE)
            {
                int blockSize = Math.min( eventsToRemove.length- i, STEP_SIZE);
                Reservation[] eventBlock = new Reservation[blockSize];
                System.arraycopy( eventsToRemove,i, eventBlock,0, blockSize);
                clientFacade.removeObjects( eventBlock);
            }
        }
	}
	private boolean isOlderThan( Reservation event, Date maxAllowedDate )
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