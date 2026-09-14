package org.rapla.plugin.archiver.server;

import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.ApiKeyScopeContext;
import org.rapla.server.RemoteSession;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbsql.DBOperator;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import java.time.LocalDateTime;
public class ArchiverServiceImpl  implements ArchiverService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverServiceImpl.class);
    @Autowired
    RemoteSession session;
    @Autowired
    CommandScheduler scheduler;
    @Autowired
    RaplaFacade raplaFacade;
    @Autowired
    org.rapla.storage.SyncStorageOperator syncOperator;
    @Autowired
    ImportExportManager importExportManager;
    private final HttpServletRequest request;

    @Autowired
    public ArchiverServiceImpl(HttpServletRequest request)
    {
        this.request = request;
    }
	
	/** can be overriden to check if user has admin rights when triggered as RemoteService
	 * @throws RaplaException */
	protected void checkAccess() throws RaplaException
	{
        User user = session.checkAndGetUser(request);
        if ( user == null || !user.isAdmin())
        {
            throw new RaplaSecurityException("ArchiverService can only be triggered by admin users");
        }
        // backup/restore/delete bypass the operator dispatch chokepoint (saveData / bulk remove),
        // so the per-entity api-key scope guard never fires — gate them explicitly on write_all.
        ApiKeyScopeContext.requireWriteAllForBulk("archiver operation");
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

    @Override
    public void backupNow() throws RaplaException {
        backupNowSync();
    }

	public void backupNowSync() throws RaplaException {
        checkAccess();
        if (!isExportEnabled())
        {
            throw new RaplaException("Export not enabled");
        }
        importExportManager.doExport();
	}

    @Override
    public void restore() throws RaplaException {
        restoreSync();
    }

	public void restoreSync() throws RaplaException {
        checkAccess();
        if (!isExportEnabled())
        {
            throw new RaplaException("Export not enabled");
        }
        // We only do an import here
        importExportManager.doImport();
	}

    @Override
    public void delete(Integer removeOlderInDays) throws RaplaException {
        deleteSync(removeOlderInDays);
    }

	public void deleteSync(Integer removeOlderInDays) throws RaplaException {
        checkAccess();
        delete(removeOlderInDays, this.raplaFacade, this.syncOperator);
	}

    static public void delete(Integer removeOlderInDays, RaplaFacade raplaFacade, org.rapla.storage.SyncStorageOperator syncOperator) throws RaplaException
    {
        LocalDateTime endDate = raplaFacade.today().atStartOfDay().minusDays(removeOlderInDays);
        User[] owners = raplaFacade.getUsers();
        Collection<Reservation> events = syncOperator.getReservationsSync(null, null, owners, null, endDate, null);
        List<Reservation> toRemove = new ArrayList<>();
        for (Reservation event : events)
        {
            if (!RaplaComponent.isTemplate(event) && isOlderThan(event, endDate))
            {
                toRemove.add(event);
            }
        }
        if (toRemove.size() > 0)
        {
            LOGGER.info("Removing {} old events.", toRemove.size());
            Reservation[] eventsToRemove = toRemove.toArray(Reservation.RESERVATION_ARRAY);
            int STEP_SIZE = 100;
            for (int i = 0; i < eventsToRemove.length; i += STEP_SIZE)
            {
                int blockSize = Math.min(eventsToRemove.length - i, STEP_SIZE);
                Reservation[] eventBlock = new Reservation[blockSize];
                System.arraycopy(eventsToRemove, i, eventBlock, 0, blockSize);
                raplaFacade.removeObjects(eventBlock);
            }
        }
    }

    static private boolean isOlderThan( Reservation event, LocalDateTime maxAllowedDate )
	{
        Appointment[] appointments = event.getAppointments();
        for ( int i=0;i<appointments.length;i++)
        {
            Appointment appointment = appointments[i];
            LocalDateTime start = appointment.getStart();
            LocalDateTime end = appointment.getMaxEnd();
            if ( start == null || end == null )
            {
                return false;
            }
            if ( end.isAfter( maxAllowedDate))
            {
                return false;
            }
            if ( start.isAfter( maxAllowedDate))
            {
                return false;
            }
        }
        return true;
	}

}