/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.plugin.abstractcalendar.server;

import java.util.Calendar;
import java.util.Date;

import org.rapla.components.calendarview.Block;
import org.rapla.framework.RaplaContext;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;


public class HTMLRaplaBuilder extends RaplaBuilder {
    static String COLOR_NO_RESOURCE = "#BBEEBB";
    int m_rowsPerHour = 4;
    /** shared calendar instance. Only used for temporary stored values. */
    String m_html;
    int index = 0;

    public HTMLRaplaBuilder(RaplaContext sm) {
        super(sm);
    }

    @Override
    protected boolean isExceptionsExcluded() {
        return true;
    }

    @Override
    protected Block createBlock(RaplaBlockContext blockContext, Date start, Date end) {
        HTMLRaplaBlock block = createBlock();
        block.setIndex( index ++ );
        block.setStart(start);
        block.setEnd(end);
        block.contextualize(blockContext);

        Calendar calendar = getRaplaLocale().createCalendar();
        calendar.setTime(start);
        int row = (int) (
            calendar.get(Calendar.HOUR_OF_DAY)* m_rowsPerHour
            + Math.round((calendar.get(Calendar.MINUTE) * m_rowsPerHour)/60.0)
            );
        block.setRow(row);
        block.setDay(calendar.get(Calendar.DAY_OF_WEEK));

        calendar.setTime(block.getEnd());
        int endRow = (int) (
            calendar.get(Calendar.HOUR_OF_DAY)* m_rowsPerHour
            + Math.round((calendar.get(Calendar.MINUTE) * m_rowsPerHour)/60.0)
            );
        int rowCount = endRow -row;
        block.setRowCount(rowCount);
        //System.out.println("Start " + start + " End " + end);
        //System.out.println("Block " + block.getReservation().getName(null)
        //                   + " Row: " + row + " Endrow: " + endRow + " Rowcount " + rowCount );
        return block;
    }

    protected HTMLRaplaBlock createBlock() {
        HTMLRaplaBlock block = new HTMLRaplaBlock();
        return block;
    }

}
