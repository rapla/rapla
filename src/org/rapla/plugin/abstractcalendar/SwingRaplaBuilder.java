
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

package org.rapla.plugin.abstractcalendar;

import java.util.Date;

import org.rapla.components.calendarview.Block;
import org.rapla.framework.RaplaContext;


public class SwingRaplaBuilder extends RaplaBuilder
{
    public SwingRaplaBuilder(RaplaContext sm) 
    {
        super(sm);
    }

    /**
     * @see org.rapla.plugin.abstractcalendar.RaplaBuilder#createBlock(calendar.RaplaBuilder.RaplaBlockContext, java.util.Date, java.util.Date)
     */
    protected Block createBlock(RaplaBlockContext blockContext, Date start, Date end) {
        SwingRaplaBlock block = new SwingRaplaBlock();
        block.contextualize(blockContext);
        block.setStart(start);
        block.setEnd(end);
        return block;
    }
}
