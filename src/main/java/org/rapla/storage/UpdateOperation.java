package org.rapla.storage;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.storage.EntityReferencer;

public interface UpdateOperation {
    String getCurrentId();
    Class<? extends Entity> getType();
    EntityReferencer.ReferenceInfo getReference();
}