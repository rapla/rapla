package org.rapla.entities;


import java.time.LocalDateTime;

public interface LastChangedTimestamp {

    /** returns the date of last change of the object. UTC. */
    LocalDateTime getLastChanged();

}
