package org.rapla.storage.server;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;

public interface ImportExportEntity extends Entity<ImportExportEntity>
{
    public static final RaplaType<ImportExportEntity> TYPE = new RaplaType<>(ImportExportEntity.class, "IMPORT_EXPORT");

    String getExternalSystem();

    String getRaplaId();

    String getData();

    String getContext();
    
    int getDirection();

}
