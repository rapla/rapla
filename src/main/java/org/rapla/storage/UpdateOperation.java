package org.rapla.storage;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;

public interface UpdateOperation {
    String getCurrentId();
    RaplaType getRaplaType();
}