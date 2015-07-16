package org.rapla.gwt.client;

import java.util.Set;

import javax.inject.Inject;

import org.rapla.framework.logger.Logger;

public class Bootstrap {
   @Inject private Logger logger;
   @Inject Set<Greeter> greeters;
   public void start()
   {
       for ( Greeter greeter:greeters)
       {
           logger.info(greeter.greet());
       }
   }
}
