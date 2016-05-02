package org.rapla.server.internal.console;

import org.rapla.framework.RaplaException;

public interface ImportExportManagerContainer
{
    void doImport() throws RaplaException;

    void doExport() throws RaplaException;

    void dispose();
}
