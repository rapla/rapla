package org.rapla.entities.storage.internal;

import org.rapla.entities.storage.StoredArtifact;

import java.time.LocalDateTime;

/**
 * Deliberately NOT {@link org.rapla.entities.internal.ModifiableTimestamp}: that interface routes
 * entities into the EntityHistory / update-history replay on dispatch, and artifacts are
 * read-through server-only entities that must stay out of it (PRD 098 OQ1). Timestamps are set
 * explicitly by the catalog service at save time.
 */
public class StoredArtifactImpl extends SimpleEntity implements StoredArtifact
{
    private String kind;
    private String name;
    private String body;
    private String metadata;
    private LocalDateTime createDate;
    private LocalDateTime lastChanged;

    public StoredArtifactImpl()
    {
    }

    public StoredArtifactImpl(String kind, String name)
    {
        this.kind = kind;
        this.name = name;
        setId(StoredArtifact.createId(kind, name));
    }

    @Override public Class<StoredArtifact> getTypeClass()
    {
        return StoredArtifact.class;
    }

    @Override
    public StoredArtifactImpl clone()
    {
        StoredArtifactImpl clone = new StoredArtifactImpl();
        super.deepClone(clone);
        clone.kind = kind;
        clone.name = name;
        clone.body = body;
        clone.metadata = metadata;
        clone.createDate = createDate;
        clone.lastChanged = lastChanged;
        return clone;
    }

    @Override
    public String getKind()
    {
        return kind;
    }

    public void setKind(String kind)
    {
        this.kind = kind;
    }

    @Override
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public String getBody()
    {
        return body;
    }

    public void setBody(String body)
    {
        this.body = body;
    }

    @Override
    public String getMetadata()
    {
        return metadata;
    }

    public void setMetadata(String metadata)
    {
        this.metadata = metadata;
    }

    @Override
    public LocalDateTime getCreateDate()
    {
        return createDate;
    }

    public void setCreateDate(LocalDateTime date)
    {
        this.createDate = date;
    }

    @Override
    public LocalDateTime getLastChanged()
    {
        return lastChanged;
    }

    public void setLastChanged(LocalDateTime date)
    {
        this.lastChanged = date;
    }
}
