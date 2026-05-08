package org.rapla.server.internal.console;

import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.storage.ImportExportManager;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;


public class ImportExportManagerContainerImpl implements ImportExportManagerContainer
{

    CommandScheduler scheduler;
    Supplier<ImportExportManager> importExportManagerProvider;
    @Autowired
    public ImportExportManagerContainerImpl(CommandScheduler scheduler,ServerStorageSelector backendContext) throws RaplaInitializationException
    {
        this.scheduler = scheduler;
        this.importExportManagerProvider = backendContext.getImportExportManager();
    }

    @Override
    public void doImport() throws  RaplaException
    {
        importExportManagerProvider.get().doImport();
    }

    @Override
    public void doExport() throws RaplaException
    {
        importExportManagerProvider.get().doExport();
    }

    @Override
    public void dispose() {
        ((DefaultScheduler)scheduler).dispose();
    }
    

}
