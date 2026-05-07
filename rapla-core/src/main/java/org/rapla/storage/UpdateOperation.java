package org.rapla.storage;

import org.rapla.entities.Entity;
import org.rapla.entities.storage.ReferenceInfo;

public interface UpdateOperation<T extends Entity> {
    Class<T> getType();
    ReferenceInfo<T> getReference();
}