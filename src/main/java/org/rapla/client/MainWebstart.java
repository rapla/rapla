/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.ConfigTools;


final public class MainWebstart  
{
    public static void main(String[] args) {
    	MainWebclient main = new MainWebclient();
        try {
        	main.init( ConfigTools.webstartConfigToURL( ""),StartupEnvironment.WEBSTART);
            String startupUser = System.getProperty("jnlp.org.rapla.startupUser");
            main.setStartupUser( startupUser);
            String moduleId = System.getProperty("jnlp.org.rapla.moduleId");
            main.setModuleId( moduleId );
        	main.startRapla();
        } catch (Throwable ex) {
            main.getLogger().error("Couldn't start Rapla",ex);
            if (main.raplaContainer != null)
            {
                main.raplaContainer.dispose();
            }
            System.out.flush();
            try
            {
                Thread.sleep( 2000 );
            }
            catch ( InterruptedException e )
            {
            }
            System.exit(1);
           
        }
    }
}
