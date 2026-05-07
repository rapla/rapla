/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.entities.internal;

import org.rapla.entities.Timestamp;
import org.rapla.entities.User;

import java.util.Date;

public interface ModifiableTimestamp extends Timestamp {
    /** updates the last-changed timestamp */
    void setLastChanged(Date date);
    void setCreateDate(Date date);
    void setLastChangedBy( User user);

    /** {@code LocalDateTime} variants. UTC. Distinct names avoid `null`-passing ambiguity. */
    default void setLastChangedLocalDateTime(java.time.LocalDateTime date) {
        setLastChanged(date == null ? null : org.rapla.components.util.DateTools.toDate(date));
    }
    default void setCreateDateLocalDateTime(java.time.LocalDateTime date) {
        setCreateDate(date == null ? null : org.rapla.components.util.DateTools.toDate(date));
    }
}
