package org.rapla.entities;

import java.util.Date;

public interface LastChangedTimestamp {

    /** returns the date of last change of the object. */
    public abstract Date getLastChanged();

}