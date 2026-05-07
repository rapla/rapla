
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.plugin.abstractcalendar.client.swing;

import org.rapla.RaplaResources;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;

public class SwingRaplaBuilder extends RaplaBuilder
{
    public SwingRaplaBuilder(RaplaFacade raplaFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, AppointmentFormater appointmentFormater)
    {
        super(raplaLocale, raplaFacade, i18n, logger, appointmentFormater);
        this.setBlockCreator(( blockContext, start, end)->new SwingRaplaBlock( blockContext, start, end));
    }

}
