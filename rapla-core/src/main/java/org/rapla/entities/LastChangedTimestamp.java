package org.rapla.entities;


import org.rapla.components.util.DateTools;

import java.time.LocalDateTime;
import java.util.Date;

public interface LastChangedTimestamp {

    /** returns the date of last change of the object. */
    Date getLastChanged();

    /** {@code java.time} variant of {@link #getLastChanged()}. UTC.
     *  Default implementation converts via {@link DateTools#toLocalDateTime(Date)};
     *  impls may override to expose a stored {@code LocalDateTime} field directly. */
    default LocalDateTime getLastChangedAsLocalDateTime() {
        Date d = getLastChanged();
        return d == null ? null : DateTools.toLocalDateTime(d);
    }

}