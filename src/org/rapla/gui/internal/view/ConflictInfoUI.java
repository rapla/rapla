/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.gui.internal.view;

import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaContext;

class ConflictInfoUI extends HTMLInfo<Conflict> {
    public ConflictInfoUI(RaplaContext sm) {
        super(sm);
    }

    protected String createHTMLAndFillLinks(Conflict conflict,LinkController controller) {
        StringBuffer buf = new StringBuffer();

        buf.append( "<span style=\"font-color:#B00202;font-weight:bold;\">");
        buf.append( getName(conflict.getAllocatable() ));
        buf.append( " " );
        buf.append( getAppointmentFormater().getSummary(conflict.getAppointment1()));
        buf.append( "</span>");
        buf.append( "<br>" );
        buf.append( "'" );
        buf.append( getName(conflict.getReservation1() ));
        buf.append( "'" );
        buf.append( "<br>" );
        buf.append( getString("with"));
        buf.append( " '" );
        buf.append( getName(conflict.getReservation2() ));
        buf.append( "' " );
        buf.append( " " + getString("reservation.owner") + " ");
        buf.append( conflict.getUser2().getUsername());

        return buf.toString();
    }

    public String getTooltip( Conflict object ) {
        return createHTMLAndFillLinks( object, null);
    }
}

