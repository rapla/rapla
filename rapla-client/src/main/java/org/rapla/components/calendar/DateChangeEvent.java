/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.components.calendar;
import java.util.EventObject;
import java.time.LocalDateTime;
public class DateChangeEvent extends EventObject {
    private static final long serialVersionUID = 1L;

    LocalDateTime m_date;
    public DateChangeEvent(Object source,LocalDateTime date) {
        super(source);
        m_date = date;
    }
    public LocalDateTime getDate() {
        return m_date;
    }
}
