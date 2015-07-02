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

/**  Implement this interface if you want to highlight special times or
    show tooltip for some times.
 */
public interface TimeRenderer {
    /** Specifies a special background color for the passed time.
        Return null if you want to use the default color.*/
    public Color getBackgroundColor(int hourOfDay,int minute);
    /** Specifies a tooltip text for the passed time.
        Return null if you don't want to use a tooltip for this time.*/
    public String getToolTipText(int hourOfDay,int minute);
    
    /** returns a formated text of the duration can return null if the duration should not be appended, e.g. if its &lt; 0*/
    public String getDurationString(int durationInMinutes);

}
