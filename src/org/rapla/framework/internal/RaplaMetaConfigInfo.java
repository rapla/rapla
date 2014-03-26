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
package org.rapla.framework.internal;

import java.util.HashMap;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.internal.RaplaClientServiceImpl;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.RemoteOperator;


public class RaplaMetaConfigInfo  extends HashMap<String,ComponentInfo> {
    private static final long serialVersionUID = 1L;
    
    public RaplaMetaConfigInfo() {
        put( "rapla-client", new ComponentInfo(RaplaClientServiceImpl.class.getName(),ClientServiceContainer.class.getName()));
        put( "resource-bundle",new ComponentInfo(I18nBundleImpl.class.getName(),I18nBundle.class.getName()));
        put( "facade",new ComponentInfo(FacadeImpl.class.getName(),ClientFacade.class.getName()));
        put( "remote-storage",new ComponentInfo(RemoteOperator.class.getName(),new String[] {StorageOperator.class.getName(), CachableStorageOperator.class.getName()}));
        // now the server configurations
        put( "rapla-server", new ComponentInfo("org.rapla.server.internal.ServerServiceImpl",new String[]{"org.rapla.server.ServerServiceContainer"}));
        put( "file-storage",new ComponentInfo("org.rapla.storage.dbfile.FileOperator",new String[] {StorageOperator.class.getName(), CachableStorageOperator.class.getName()}));
        put( "db-storage",new ComponentInfo("org.rapla.storage.dbsql.DBOperator",new String[] {StorageOperator.class.getName(), CachableStorageOperator.class.getName()}));
        put( "sql-storage",new ComponentInfo("org.rapla.storage.dbsql.DBOperator",new String[] {StorageOperator.class.getName(), CachableStorageOperator.class.getName()}));
        put( "importexport", new ComponentInfo("org.rapla.storage.impl.server.ImportExportManagerImpl",ImportExportManager.class.getName()));
    }
}


