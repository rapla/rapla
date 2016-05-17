package org.rapla.storage.impl.server;

import javax.inject.Inject;

import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;

@DefaultImplementation(of=ImportExportManager.class,context = InjectionContext.server)
public class ImportExportManagerDefaultImpl implements ImportExportManager
{

    private final ImportExportManager importExportManager;

    @Inject
    public ImportExportManagerDefaultImpl(ServerStorageSelector selector)
    {
        importExportManager = selector.getImportExportManager().get();
    }
    @Override public void doImport() throws RaplaException
    {
        importExportManager.doImport();
    }

    @Override public void doExport() throws RaplaException
    {
        importExportManager.doExport();
    }

    @Override public CachableStorageOperator getSource() throws RaplaException
    {
        return importExportManager.getSource();
    }

    @Override public CachableStorageOperator getDestination() throws RaplaException
    {
        return importExportManager.getDestination();
    }
}
