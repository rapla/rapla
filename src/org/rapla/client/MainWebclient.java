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
import java.net.URL;
import java.net.URLDecoder;

import org.rapla.ConnectInfo;
import org.rapla.RaplaMainContainer;
import org.rapla.RaplaStartupEnvironment;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.logger.ConsoleLogger;
import org.rapla.framework.logger.Logger;


public class MainWebclient  
{
    /** The default config filename for client-mode raplaclient.xconf*/
    public final static String CLIENT_CONFIG_SERVLET_URL = "rapla/raplaclient.xconf";

    private Logger logger = new ConsoleLogger(ConsoleLogger.LEVEL_WARN).getChildLogger("init");
    RaplaStartupEnvironment env = new RaplaStartupEnvironment();
    Container raplaContainer;

    String startupUser;
    
    

    void init(URL configURL,int mode) throws Exception {
        env.setStartupMode( mode );
        env.setConfigURL( configURL );
        env.setBootstrapLogger( getLogger() );
    }

    public String getStartupUser() 
    {
        return startupUser;
    }

    public void setStartupUser(String startupUser) 
    {
        this.startupUser = startupUser;
    }
    
    void startRapla(final String id) throws Exception {
        ConnectInfo connectInfo = null;

        try
        {
            if ( startupUser != null)
            {
                String decoded = URLDecoder.decode( startupUser, "UTF-8");
                getLogger().warn("Starting rapla with username " + startupUser);
                connectInfo = new ConnectInfo( decoded, "".toCharArray());
            }
            else
            {
                getLogger().warn("Starting rapla with login screen.");
            }
        } catch (Throwable ex)
        {
            getLogger().error("Error reading system property " , ex);
        }
        startRapla(id, connectInfo);
    }

    protected void startRapla(final String id, ConnectInfo connectInfo) throws Exception, RaplaContextException {
        raplaContainer = new RaplaMainContainer( env);
        ClientServiceContainer clientContainer = raplaContainer.lookup(ClientServiceContainer.class, id );
        ClientService client =  clientContainer.getContext().lookup( ClientService.class);
        client.addRaplaClientListener(new RaplaClientListenerAdapter() {
                public void clientClosed(ConnectInfo reconnect) {
                    if ( reconnect != null) {
                        raplaContainer.dispose();
                        try {
                            startRapla(id, reconnect);
                        } catch (Exception ex) {
                            getLogger().error("Error restarting client",ex);
                            exit();
                        }
                    } else {
                        exit();
                    }
                }
                public void clientAborted()
                {
                    exit();
                }
            });
        clientContainer.start(connectInfo);
    }
    
    public static void main(String[] args) {
    	MainWebclient main = new MainWebclient();
        try {
        	
        	main.init( new URL("http://localhost:8051/rapla/raplaclient.xconf"),StartupEnvironment.CONSOLE);
            main.startRapla("client");
        } catch (Throwable ex) {
            main.getLogger().error("Couldn't start Rapla",ex);
            main.raplaContainer.dispose();
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
    
    private void exit() {
    	if ( raplaContainer != null)
    	{
    		raplaContainer.dispose();
    	}
        if (env.getStartupMode() != StartupEnvironment.APPLET)
        {
            System.exit(0);
        }
    }

    Logger getLogger() {
        return logger;
    }
    
}
