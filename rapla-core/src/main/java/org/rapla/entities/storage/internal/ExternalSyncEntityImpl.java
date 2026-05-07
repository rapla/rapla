package org.rapla.entities.storage.internal;

import org.rapla.entities.storage.ExternalSyncEntity;

public class ExternalSyncEntityImpl extends SimpleEntity implements ExternalSyncEntity
{

    private int direction;
    private String context;
    private String data;
    private String raplaId;
    private String externalSystem;

    public ExternalSyncEntityImpl()
    {
    }

    @Override public Class<ExternalSyncEntity> getTypeClass()
    {
        return ExternalSyncEntity.class;
    }

    @Override
    public ExternalSyncEntityImpl clone()
    {
        ExternalSyncEntityImpl newImportExportEntity = new ExternalSyncEntityImpl();
        super.deepClone(newImportExportEntity);
        newImportExportEntity.context = context;
        newImportExportEntity.data = data;
        newImportExportEntity.direction = direction;
        newImportExportEntity.externalSystem = externalSystem;
        newImportExportEntity.raplaId = raplaId;
        return newImportExportEntity;
    }

    @Override
    public String getExternalSystem()
    {
        return externalSystem;
    }

    @Override
    public String getRaplaId()
    {
        return raplaId;
    }

    @Override
    public String getData()
    {
        return data;
    }

    @Override
    public String getContext()
    {
        return context;
    }

    @Override
    public int getDirection()
    {
        return direction;
    }

    public void setDirection(int direction)
    {
        this.direction = direction;
    }

    public void setContext(String context)
    {
        this.context = context;
    }

    public void setData(String data)
    {
        this.data = data;
    }

    public void setRaplaId(String raplaId)
    {
        this.raplaId = raplaId;
    }

    public void setExternalSystem(String externalSystem)
    {
        this.externalSystem = externalSystem;
    }

}
