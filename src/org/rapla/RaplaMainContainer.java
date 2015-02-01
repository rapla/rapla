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

import javax.inject.Provider;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.SimpleProvider;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteConnectionInfo;
import org.rapla.storage.dbrm.RemoteOperator;
import org.rapla.storage.dbrm.RemoteServiceCaller;
import org.rapla.storage.dbrm.RemoteServiceCallerImpl;
/**
The Rapla Main Container class for the basic container for Rapla specific services and the rapla plugin architecture.
The rapla container has only one instance at runtime. Configuration of the RaplaMainContainer is done in the rapla*.xconf
files. Typical configurations of the MainContainer are

 <ol>
 <li>Client: A ClientContainerService, one facade and a remote storage ( automatically pointing to the download server in webstart mode)</li>
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
  @see LocaleSelector
 */
public class RaplaMainContainer extends ContainerImpl
{
    final RemoteConnectionInfo remoteConnectionInfo = new RemoteConnectionInfo();
    RemoteServiceCaller caller;

    public RaplaMainContainer(  StartupEnvironment env) throws Exception
    {
        this(  env, new SimpleProvider<RemoteServiceCaller>());
    }
    
    protected RaplaMainContainer(StartupEnvironment env,  Provider<RemoteServiceCaller> caller) throws Exception{
        super( env.getBootstrapLogger() , caller);
        addContainerProvidedComponentInstance( StartupEnvironment.class, env);
        URL downloadURL = env.getDownloadURL();
        remoteConnectionInfo.setServerURL( downloadURL.toURI().toString());
     	addContainerProvidedComponentInstance( RemoteConnectionInfo.class, remoteConnectionInfo);
        addContainerProvidedComponent( StorageOperator.class, RemoteOperator.class);
        addContainerProvidedComponent( ClientFacade.class, FacadeImpl.class);
        if ( caller instanceof SimpleProvider )
        {
            SimpleProvider<RemoteServiceCaller> simpleProvider = (SimpleProvider<RemoteServiceCaller>) caller;
            if ( simpleProvider.get() == null)
            {
                String className = RemoteServiceCallerImpl.class.getName();
                RemoteServiceCaller instanciate = (RemoteServiceCaller) instanciate(className, null, getLogger());
                simpleProvider.setValue(instanciate);
            }
        }
        initialize();
    }

    
	        
 }

