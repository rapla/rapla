package org.rapla.server.internal.console;

import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.storage.ImportExportManager;

import javax.inject.Inject;
import javax.inject.Provider;


@DefaultImplementation(of=ImportExportManagerContainer.class,context = InjectionContext.server,export = true)
public class ImportExportManagerContainerImpl implements ImportExportManagerContainer
{

    CommandScheduler scheduler;
    Provider<ImportExportManager> importExportManagerProvider;
    @Inject
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
