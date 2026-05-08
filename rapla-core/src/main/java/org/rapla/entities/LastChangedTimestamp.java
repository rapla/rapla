package org.rapla.entities;


import org.rapla.components.util.DateTools;

import java.time.LocalDateTime;
import java.util.Date;

public interface LastChangedTimestamp {

    /** returns the date of last change of the object. UTC. */
    LocalDateTime getLastChangedAsLocalDateTime();

    /** Legacy {@code Date} accessor — delegates to {@link #getLastChangedAsLocalDateTime()}. */
    default Date getLastChanged() {
        LocalDateTime ldt = getLastChangedAsLocalDateTime();
        return ldt == null ? null : DateTools.toDate(ldt);
    }

}