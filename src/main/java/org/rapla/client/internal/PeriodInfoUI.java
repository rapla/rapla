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
package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Period;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

public class PeriodInfoUI extends HTMLInfo<Period> {
    public PeriodInfoUI(RaplaFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        super(i18n, raplaLocale, facade, logger);
    }

    public String createHTMLAndFillLinks(Period period,LinkController controller, User user) {
        Collection<Row> att = new ArrayList<>();
        RaplaLocale loc = getRaplaLocale();

        att.add(new Row(getString("name"), strong( encode( getName( period ) ))));
        final Date periodStart = period.getStart();
        if ( periodStart != null)
        {
            att.add(new Row(
                            getString("start_date")
                            ,loc.getWeekday( periodStart )
                            + ' '
                            + loc.formatDate( periodStart)
                            )
                    );
        }
        final Date periodEnd = period.getEnd();
        if ( periodEnd != null)
        {
            att.add(new Row(
                            getString("end_date"),
                            loc.getWeekday( DateTools.subDay(periodEnd) )
                            + ' '
                            + loc.formatDate( DateTools.subDay(periodEnd) )
                            )
                    );
        }
        return createTable(att, false);
    }

    @Override
    public String getTooltip(Period object, User user) {
        return createHTMLAndFillLinks( object, null, user);
    }

}

