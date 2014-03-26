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

package org.rapla.gui.internal;
import java.awt.Color;

import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.WeekendHighlightRenderer;
import org.rapla.entities.domain.Period;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;

public class RaplaDateRenderer extends RaplaComponent implements DateRenderer {
    protected WeekendHighlightRenderer renderer = new WeekendHighlightRenderer();
    protected Color periodColor = new Color(0xc5,0xda,0xdd);
    protected PeriodModel periodModel;

    public RaplaDateRenderer(RaplaContext sm) {
        super(sm);
        periodModel = getPeriodModel();
    }

    
    public RenderingInfo getRenderingInfo(int dayOfWeek,int day,int month, int year)
    {
        Period period = periodModel.getPeriodFor(getRaplaLocale().toRaplaDate(year,month,day));
        if (period != null)
        {
            Color backgroundColor = periodColor;
            Color foregroundColor = Color.BLACK;
            String tooltipText = "<html>" + getString("period") + ":<br>" + period.getName(getI18n().getLocale()) + "</html>";
            return new RenderingInfo(backgroundColor, foregroundColor, tooltipText);
        }
        return renderer.getRenderingInfo(dayOfWeek,day,month,year);
    }

}
