package org.rapla.examples;

import org.rapla.RaplaClient;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.ConsoleLogger;
import org.rapla.rest.client.internal.isodate.ISODateTimeFormat;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.dbrm.RemoteOperator;
import org.rapla.storage.dbrm.RemoteStorage;

import java.util.Collection;
import java.util.Locale;
/** Simple demonstration for connecting your app and importing some users. See sources*/
public class RaplaConnectorTest
{
    public static void main(String[] args) {
        final ConsoleLogger logger = new ConsoleLogger( ConsoleLogger.LEVEL_INFO);

        try
        {
            String user = args[0];
            String password = args[1];
            // and calls rapla/rpc/methodNames for interacting
            //StartupEnvironment env = new SimpleConnectorStartupEnvironment( "localhost", 8051,"/", false, logger);
            StartupEnvironment env = new SimpleConnectorStartupEnvironment( "rapla-test.dhbw.de", 443,"/", true, logger);
            RaplaClient container = new RaplaClient( env);

            // get an interface to the facade and login
            ClientFacade facade = container.getFacade();
            if ( !facade.login( user, password.toCharArray()) ) {
                throw new RaplaException("Can't login");
            }

            // query resouce
            RaplaFacade raplaFacade = facade.getRaplaFacade();
            RemoteOperator operator = (RemoteOperator) raplaFacade.getOperator();
            String lastSyncedTime = "2025-08-05T09:19:40.506Z";

            //Allocatable firstResource = raplaFacade.getAllocatables() [0] ;
            //logger.info( firstResource.getName( Locale.getDefault()));
            while (true) {
                // This will return an UpdateEvent with all resources that should be synced for the user
                UpdateEvent updateEvent = operator.refreshEventsSync(lastSyncedTime);
                logger.info("Update Event: " + updateEvent);
                lastSyncedTime = new ISODateTimeFormat().formatTimestamp(updateEvent.getLastValidated());
                Collection<Entity> storeObjects = updateEvent.getStoreObjects();
                for ( Entity entity : storeObjects ) {
                    if ( entity instanceof Reservation) {
                        Reservation reservation = (Reservation) entity;
                        logger.info("Veranstaltung: " + reservation.getName(Locale.getDefault()));
                        Allocatable[] allocatables = reservation.getAllocatables();
                        for ( Allocatable allocatable : allocatables ) {
                            logger.info("  Ressource: " + allocatable.getName(Locale.getDefault()));
                        }
                        Appointment[] appointments = reservation.getAppointments();
                        int i = 1;
                        for (Appointment appointment : appointments) {
                            logger.info("  Terminblock " + i +  " Start: " + appointment.getStartDateTime() + " Ende: " + appointment.getEndDateTime() );
                            i++;
                        }
                    }
                }
                Thread.sleep(15000);
                if ( false) // replace with your condition to stop the loop
                {
                    break;
                }
            }

            // cleanup the Container
            container.dispose();
        }
        catch ( Exception e )
        {
            logger.error("Could not start test ",  e );
        }

    }

}
