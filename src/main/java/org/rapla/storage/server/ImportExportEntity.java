package org.rapla.storage.server;

import org.rapla.entities.Entity;

public interface ImportExportEntity extends Entity<ImportExportEntity>
{

    String getExternalSystem();

    String getRaplaId();

    String getData();

    String getContext();
    
    int getDirection();

}
