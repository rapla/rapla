package org.rapla.storage;

import org.rapla.entities.RaplaObject;

public interface UpdateOperation {
    public RaplaObject getCurrent();
}