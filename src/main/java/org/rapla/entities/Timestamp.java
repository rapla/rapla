/*--------------------------------------------------------------------------*
 | Copyright (C) 2014  Christopher Kohlhaas                                 |
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

package org.rapla.entities;

import org.rapla.components.util.DateTools;
import org.rapla.entities.storage.ReferenceInfo;

import java.time.LocalDateTime;
import java.util.Date;

public interface Timestamp extends LastChangedTimestamp {
    /** returns the creation date of the object. */
    Date getCreateDate();
    ReferenceInfo<User> getLastChangedBy();

    /** {@code java.time} variant of {@link #getCreateDate()}. UTC.
     *  Default impl converts via {@link DateTools#toLocalDateTime(Date)}. */
    default LocalDateTime getCreateDateAsLocalDateTime() {
        Date d = getCreateDate();
        return d == null ? null : DateTools.toLocalDateTime(d);
    }
}
