package org.rapla.examples;

import org.rapla.RaplaClient;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.ConsoleLogger;

import java.util.Locale;
/** Simple demonstration for connecting your app and importing some users. See sources*/
public class RaplaConnectorTest
{
    public static void main(String[] args) {
        final ConsoleLogger logger = new ConsoleLogger( ConsoleLogger.LEVEL_INFO);

        try
        {
            // Connects to http://localhost:8051/
            // and calls rapla/rpc/methodNames for interacting 
            StartupEnvironment env = new SimpleConnectorStartupEnvironment( "localhost", 8051,"/", false, logger);
            RaplaClient container = new RaplaClient( env);

            // get an interface to the facade and login
            ClientFacade facade = container.getFacade();

            if ( !facade.login( "admin", "".toCharArray()) ) {
                throw new RaplaException("Can't login");
            }

            // query resouce
            Allocatable firstResource = facade.getRaplaFacade().getAllocatables() [0] ;
            logger.info( firstResource.getName( Locale.getDefault()));

            // cleanup the Container
            container.dispose();
        }
        catch ( Exception e )
        {
            logger.error("Could not start test ",  e );
        }

    }

}
