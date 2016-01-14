package org.rapla.storage;

import org.rapla.entities.Entity;
import org.rapla.entities.storage.ReferenceInfo;

public interface UpdateOperation {
    String getCurrentId();
    Class<? extends Entity> getType();
    ReferenceInfo getReference();
}