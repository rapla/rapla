
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

package org.rapla.plugin.abstractcalendar.client.swing;

import java.util.Date;

import org.rapla.RaplaResources;
import org.rapla.components.calendarview.Block;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;

public class SwingRaplaBuilder extends RaplaBuilder
{
    RaplaImages images;
    
    public SwingRaplaBuilder(RaplaContext context) throws RaplaContextException 
    {
        super(context.lookup(RaplaLocale.class),context.lookup(ClientFacade.class),context.lookup(RaplaResources.class), context.lookup(Logger.class),context.lookup( AppointmentFormater.class), context.lookup(PermissionController.class));
        this.images= context.lookup( RaplaImages.class);
    }

    /**
     * @see org.rapla.plugin.abstractcalendar.RaplaBuilder#createBlock(calendar.RaplaBuilder.RaplaBlockContext, java.util.Date, java.util.Date)
     */
    protected Block createBlock(RaplaBlockContext blockContext, Date start, Date end) {
        SwingRaplaBlock block = new SwingRaplaBlock();
        block.contextualize(blockContext);
        block.setImages( images);
        block.setStart(start);
        block.setEnd(end);
        return block;
    }
}
