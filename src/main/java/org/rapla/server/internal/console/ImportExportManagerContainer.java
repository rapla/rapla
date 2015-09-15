package org.rapla.server.internal.console;

import javax.sql.DataSource;

import org.rapla.components.i18n.server.locales.I18nLocaleLoadUtil;
import org.rapla.framework.RaplaException;
import org.rapla.framework.SimpleProvider;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.InjectionContext;
import org.rapla.server.ServerService;
import org.rapla.server.internal.RaplaJNDIContext;
import org.rapla.server.internal.ServerServiceImpl.ServerContainerContext;
import org.rapla.server.internal.ServerStarter;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbrm.RemoteServiceCaller;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import java.util.Arrays;
import java.util.Collection;

public class ImportExportManagerContainer extends ContainerImpl{

    private Runnable shutdownCommand;

    public ImportExportManagerContainer(Logger logger, RaplaJNDIContext jndi) throws RaplaException 
    {
        super(logger, new SimpleProvider<RemoteServiceCaller>());
        shutdownCommand = (Runnable) jndi.lookup("rapla_shutdown_command", false);
        ServerContainerContext backendContext = ServerStarter.createBackendContext(logger, jndi);
        String fileDatasource = backendContext.getFileDatasource();
        if ( fileDatasource != null)
        {
            addContainerProvidedComponentInstance( ServerService.ENV_RAPLAFILE, fileDatasource );
            addContainerProvidedComponent( FileOperator.class, FileOperator.class);
        }
        else
        {
            throw new RaplaException("No file configured for import/export");
        }
        DataSource dbDatasource = backendContext.getDbDatasource();
        if ( dbDatasource != null)
        {
            addContainerProvidedComponentInstance( DataSource.class, dbDatasource );
            addContainerProvidedComponent( DBOperator.class, DBOperator.class);
        }
        else
        {
            throw new RaplaException("No database configured for import/export");
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

    protected Collection<InjectionContext> getSupportedContexts()
    {
        return Arrays.asList(InjectionContext.server);
    }

    public void dispose() {
        super.dispose();
        if ( shutdownCommand != null)
        {
            shutdownCommand.run();
        }        
    }
    

}
