package org.rapla.server.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.components.util.CommandScheduler;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.util.Map;


@Singleton
//@DefaultImplementation(of = StorageOperator.class,context = InjectionContext.server)
//@DefaultImplementation(of = CachableStorageOperator.class,context = InjectionContext.server)
public class ServerStorageSelector implements Provider<CachableStorageOperator>
{
    final ServerServiceImpl.ServerContainerContext containerContext;
    FileOperator file;
    DBOperator db;

    final Logger logger;
    final RaplaResources i18n;
    final RaplaLocale raplaLocale;
    final CommandScheduler scheduler;
    final Map<String, FunctionFactory> functionFactoryMap;
    final PermissionController permissionController;
    ImportExportManager manager;

    @Inject ServerStorageSelector(ServerServiceImpl.ServerContainerContext context, ServerServiceImpl.ServerContainerContext containerContext, Logger logger,
            RaplaResources i18n, RaplaLocale raplaLocale, CommandScheduler scheduler, Map<String, FunctionFactory> functionFactoryMap,
            PermissionController permissionController)
    {

        this.containerContext = containerContext;
        this.logger = logger;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.scheduler = scheduler;
        this.functionFactoryMap = functionFactoryMap;
        this.permissionController = permissionController;
    }

    @NotNull private FileOperator createFileOperator()
    {
        final String fileDatasource = containerContext.fileDatasource != null ? containerContext.fileDatasource : "data/data.xml";
        return new FileOperator(logger, i18n, raplaLocale, scheduler, functionFactoryMap, fileDatasource, permissionController);
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
        return new Provider<ImportExportManager>()
        {
            @Override public ImportExportManager get()
            {
                return getImportExport();
            }
        };
    }

    @NotNull private DBOperator createDbOperator()
    {
        Provider<ImportExportManager> importExportMananger = getImportExportManager();
        final DataSource dbDatasource = containerContext.dbDatasource;
        return new DBOperator(logger, i18n, raplaLocale, scheduler, functionFactoryMap, importExportMananger, dbDatasource, permissionController);
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

    ;
}
