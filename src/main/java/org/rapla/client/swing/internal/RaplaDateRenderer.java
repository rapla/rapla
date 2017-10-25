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

package org.rapla.client.swing.internal;

import org.rapla.client.swing.toolkit.AWTColorUtil;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.WeekendHighlightRenderer;
import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
import org.rapla.entities.domain.Period;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.Date;
import java.util.Set;

@Singleton
@DefaultImplementation(of=DateRenderer.class,context = { InjectionContext.swing,InjectionContext.server})
public class RaplaDateRenderer implements DateRenderer {
    protected WeekendHighlightRenderer renderer = new WeekendHighlightRenderer();
    protected Color periodColor = new Color(0xc5,0xda,0xdd);
    protected PeriodModel periodModel;

    private final RaplaLocale raplaLocale;
    private final RaplaFacade facade;

    @Inject
    public RaplaDateRenderer(RaplaFacade facade, RaplaLocale raplaLocale) {
        this.facade = facade;
        this.raplaLocale = raplaLocale;

    }

    protected PeriodModel getPeriodModel() {
        if ( periodModel != null)
        {
            return periodModel;
        }
        try {
            PeriodModel model = facade.getPeriodModelFor("feiertag");
            if ( model == null)
            {
                model= facade.getPeriodModel();
            }
            periodModel = model;
            return model;
        } catch (RaplaException ex) {
            throw new UnsupportedOperationException("Service not supported in this context: " + ex.getMessage(), ex );
        }
    }

    
    public RenderingInfo getRenderingInfo(int dayOfWeek,int day,int month, int year)
    {
        final Date date = raplaLocale.toRaplaDate(year, month, day);
        PeriodModel periodModel = getPeriodModel();
        Period period = periodModel.getPeriodFor(date);
        final RenderingInfo renderingInfo = renderer.getRenderingInfo(dayOfWeek, day, month, year);
        if (period != null)
        {
            Color foregroundColor =  renderingInfo.getForegroundColor();
            Color backgroundColor = null;

            final Set<Category> categories = period.getCategories();
            if ( categories.size() > 0)
            {
                final Category first = categories.iterator().next();
                final String color = first.getAnnotation(CategoryAnnotations.KEY_NAME_COLOR);
                if ( color != null && color.length() > 1)
                {
                    final Color colorForHex = AWTColorUtil.getColorForHex(color);
                    backgroundColor = colorForHex;
                }
            }

            if ( backgroundColor == null)
            {
                backgroundColor = periodColor;
            }
            else
            {

            }
            String tooltipText = "<html>" +  period.getName(raplaLocale.getLocale()) + "</html>";
            return new RenderingInfo(backgroundColor, foregroundColor, tooltipText);
        }
        return renderingInfo;
    }

}
