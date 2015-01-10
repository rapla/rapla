package org.rapla.server.internal;

import javax.sql.DataSource;

import org.rapla.framework.RaplaException;
import org.rapla.framework.SimpleProvider;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.server.ServerService;
import org.rapla.server.internal.ServerServiceImpl.ServerBackendContext;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbrm.RemoteServiceCaller;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

public class ImportExportManagerContainer extends ContainerImpl{

    private Runnable shutdownCommand;

    public ImportExportManagerContainer(Logger logger, RaplaJNDIContext jndi) 
    {
        super(logger, new SimpleProvider<RemoteServiceCaller>());
        shutdownCommand = (Runnable) jndi.lookup("rapla_shutdown_command", false);
        ServerBackendContext backendContext = ServerStarter.createBackendContext(logger, jndi);
        if ( backendContext.fileDatasource != null)
        {
            addContainerProvidedComponentInstance( ServerService.ENV_RAPLAFILE, backendContext.fileDatasource );
            addContainerProvidedComponent( FileOperator.class, FileOperator.class);
        }
        if ( backendContext.dbDatasource != null)
        {
            addContainerProvidedComponentInstance( DataSource.class, backendContext.dbDatasource );
            addContainerProvidedComponent( DBOperator.class, DBOperator.class);
        }
        if ( backendContext.fileDatasource != null && backendContext.dbDatasource != null)
        {
            addContainerProvidedComponent( ImportExportManager.class, ImportExportManagerImpl.class);
        }
        addContainerProvidedComponent( ImportExportManager.class, ImportExportManagerImpl.class);
    }
    
    public void doImport() throws  RaplaException
    {
        getContext().lookup( ImportExportManager.class).doImport();
    }
    
    public void doExport() throws RaplaException
    {
        getContext().lookup( ImportExportManager.class).doExport();
    }

    public void dispose() {
        super.dispose();
        if ( shutdownCommand != null)
        {
            shutdownCommand.run();
        }        
    }
    

}
