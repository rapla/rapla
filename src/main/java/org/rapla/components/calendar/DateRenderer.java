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

import java.awt.Color;

/**  Implement this interface if you want to highlight special days or
    show tooltip for some days. Use {@link DateRendererAdapter} if you
    want to work with Date objects.
 */
public interface DateRenderer {
    /** Specifies a rendering info ( colors and tooltip text) for the passed day.
    Return null if you don't want to use rendering info for this day. month ranges from 1-12*/
    public RenderingInfo getRenderingInfo(int dayOfWeek,int day,int month, int year);
    
    class RenderingInfo
    {
        Color backgroundColor;
        Color foregroundColor;
        String tooltipText;
        public RenderingInfo(Color backgroundColor, Color foregroundColor, String tooltipText) {
            this.backgroundColor = backgroundColor;
            this.foregroundColor = foregroundColor;
            this.tooltipText = tooltipText;
        }
        
        public Color getBackgroundColor() {
            return backgroundColor;
        }
       
        public Color getForegroundColor() {
            return foregroundColor;
        }
        
        public String getTooltipText() {
            return tooltipText;
        }
    }
}
