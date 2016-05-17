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
package org.rapla;

import java.net.URL;

import org.rapla.client.swing.internal.dagger.DaggerClientCreator;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;

/**
The Rapla Main Container class for the basic container for Rapla specific services and the rapla plugin architecture.
The rapla container has only one instance at runtime. Configuration of the RaplaMainContainer is done in the rapla*.xconf
files. Typical configurations of the MainContainer are

 <ol>
 <li>JavaClient: A ClientContainerService, one facade and a remote storage ( automatically pointing to the download server in webstart mode)</li>
 <li>Server: A ServerContainerService (providing a facade) a messaging server for handling the connections with the clients, a storage (file or db) and an extra service for importing and exporting in the db</li>
 <li>Embedded: Configuration example follows.</li>
 </ol>
<p>
Configuration of the main container is usually done via the raplaserver.xconf 
</p>
<p>
The Main Container provides the following Services to all RaplaComponents
<ul>
<li>I18nBundle</li>
<li>AppointmentFormater</li>
<li>RaplaLocale</li>
<li>LocaleSelector</li>
</ul>
</p>

  @see I18nBundle
  @see RaplaLocale
  @see AppointmentFormater
 */
public class RaplaClient
{
    private final ClientFacade facade;

    public RaplaClient(  final URL startupUrl) throws Exception
    {
        this(  new StartupEnvironment() {
            Logger logger = RaplaBootstrapLogger.createRaplaLogger();
            @Override
            public int getStartupMode() {
                return CONSOLE;
            }
            
            @Override
            public URL getDownloadURL() throws RaplaException {
                return startupUrl;
            }
            
            @Override
            public Logger getBootstrapLogger() 
            {
                return logger;
            }
        });
    }



    public RaplaClient(  StartupEnvironment env) throws Exception
    {
        facade = DaggerClientCreator.createFacade(env);
    }

    public void dispose()
    {
        
    }

    public ClientFacade getFacade()
    {
        return facade;
    }
}

