package org.rapla.server.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.spring.RaplaServerProperties;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;


public class ServerStorageSelector implements Supplier<CachableStorageOperator>
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
    final RaplaServerProperties properties;
    ImportExportManager manager;

    @Autowired public ServerStorageSelector(ServerContainerContext containerContext, Logger logger, RaplaResources i18n, RaplaLocale raplaLocale, CommandScheduler scheduler, Map<String, FunctionFactory> functionFactoryMap,
            Set<PermissionExtension> permissionExtensions, RaplaServerProperties properties)
    {

        this.containerContext = containerContext;
        this.logger = logger;
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.scheduler = scheduler;
        this.functionFactoryMap = functionFactoryMap;
        this.permissionExtensions = permissionExtensions;
        this.properties = properties;
    }

    private void applyMergeConfig(LocalAbstractCachableOperator op)
    {
        if (properties != null && properties.getMerge() != null)
        {
            op.setBlockedMergeAttributeKeys(properties.getMerge().getBlockedSyncAttributes());
        }
    }

    @NotNull private FileOperator createFileOperator()
    {
        final String raplafile = containerContext.getMainFilesource();
        final String fileDatasource = raplafile != null ? raplafile : "data/data.xml";
        FileOperator op = new FileOperator(logger, i18n, raplaLocale, scheduler, functionFactoryMap, fileDatasource, permissionExtensions);
        applyMergeConfig(op);
        return op;
    }

    synchronized private ImportExportManager getImportExport()
    {
        if (manager == null)
        {
            manager = new ImportExportManagerImpl(logger, getFile(), getDb());
        }
        return manager;
    }

    public Supplier<ImportExportManager> getImportExportManager()
    {
        return () -> getImportExport();
    }

    @NotNull private DBOperator createDbOperator()
    {
        Supplier<ImportExportManager> importExportMananger = getImportExportManager();
        final DataSource dbDatasource = containerContext.getMainDbDatasource();
        DBOperator op = new DBOperator(logger, i18n, raplaLocale, scheduler, functionFactoryMap, importExportMananger, dbDatasource, permissionExtensions);
        applyMergeConfig(op);
        return op;
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
