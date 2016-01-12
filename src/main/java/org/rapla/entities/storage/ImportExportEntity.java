package org.rapla.entities.storage;

import org.rapla.entities.Entity;

// TODO allow entities to be stored on the server
public interface ImportExportEntity extends Entity<ImportExportEntity>
{
    String getExternalSystem();

    String getRaplaId();

    String getData();

    String getContext();
    
    int getDirection();

}
