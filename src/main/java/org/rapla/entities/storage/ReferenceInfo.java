package org.rapla.entities.storage;

import org.rapla.entities.Entity;

/** returns if the entity is refering to the Object. */

public class ReferenceInfo<T>
{
    final private String id;
    final private Class<? extends Entity> type;

    public ReferenceInfo(Entity entity) {
        this(entity.getId(), entity.getTypeClass());
    }

    public ReferenceInfo(String id, Class<? extends Entity> type) {
        super();
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public Class<? extends Entity> getType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if ( ! (obj instanceof ReferenceInfo))
        {
            return false;
        }
        return this.id.equals(((ReferenceInfo)obj).id);
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return type + ":" + id;
    }

    public boolean isReferenceOf(Entity object) {
        return id.equals(object.getId() );
    }


}
