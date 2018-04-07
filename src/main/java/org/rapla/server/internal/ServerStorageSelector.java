package org.rapla.server.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.PromiseWait;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;


@Singleton
public class ServerStorageSelector implements Provider<CachableStorageOperator>
{
    final ServerContainerContext containerContext;
    FileOperator file;
    DBOperator db;

    final Logger logger;
    final RaplaResources i18n;
    final RaplaLocale raplaLocale;
    final CommandScheduler scheduler;
    final Map<String, FunctionFactory> functionFactoryMap;
    final Set<PermissionExtension> permissionExtensions;
    ImportExportManager manager;
    final PromiseWait promiseWait;

    @Inject public ServerStorageSelector(ServerContainerContext containerContext, Logger logger, RaplaResources i18n, RaplaLocale raplaLocale, CommandScheduler scheduler, Map<String, FunctionFactory> functionFactoryMap,
            Set<PermissionExtension> permissionExtensions, PromiseWait promiseWait)
    {

        this.containerContext = containerContext;
        this.logger = logger;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.scheduler = scheduler;
        this.functionFactoryMap = functionFactoryMap;
        this.permissionExtensions = permissionExtensions;
        this.promiseWait = promiseWait;
    }

    @NotNull private FileOperator createFileOperator()
    {
        final String raplafile = containerContext.getMainFilesource();
        final String fileDatasource = raplafile != null ? raplafile : "data/data.xml";
        return new FileOperator(logger, promiseWait,i18n, raplaLocale, scheduler, functionFactoryMap, fileDatasource, permissionExtensions);
    }

    synchronized private ImportExportManager getImportExport()
    {
        if (manager == null)
        {
            manager = new ImportExportManagerImpl(logger, getFile(), getDb());
        }
        return manager;
    }

    public Provider<ImportExportManager> getImportExportManager()
    {
        return () -> getImportExport();
    }

    @NotNull private DBOperator createDbOperator()
    {
        Provider<ImportExportManager> importExportMananger = getImportExportManager();
        final DataSource dbDatasource = containerContext.getMainDbDatasource();
        return new DBOperator(logger, promiseWait,i18n, raplaLocale, scheduler, functionFactoryMap, importExportMananger, dbDatasource, permissionExtensions);
    }



    synchronized public CachableStorageOperator get()
    {
        if (containerContext.isDbDatasource())
        {
            return getDb();
        }
        else
        {
            return getFile();
        }
    }

    @NotNull private FileOperator getFile()
    {
        if (file == null)
        {
            file = createFileOperator();
        }
        return file;
    }

    @NotNull private DBOperator getDb()
    {
        if (db == null)
        {
            db = createDbOperator();
        }
        return db;
    }

}
