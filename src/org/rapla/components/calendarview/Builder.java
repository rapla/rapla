/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender                                     |
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
package org.rapla.components.calendarview;

import java.util.Date;

public interface Builder {
   /** Calculate the blocks that should be displayed in the weekview.
    * This method should not be called manually.
    * It is called by the CalendarView during the build process.
    * @see #build
    * @param start
    * @param end
    */
    void prepareBuild(Date start, Date end);
   
    /** The maximal ending-time of all blocks that should be displayed.
     Call prepareBuild first to calculate the blocks.*/
    int getMaxMinutes();
    
    /** The minimal starting-time of all blocks that should be displayed.
     Call prepareBuild first to calculate the blocks.*/
    int getMinMinutes();
   
    /** Build the calculated blocks into the weekview. This method should not be called manually.
     * It is called by the CalendarView during the build process.
     * @see #prepareBuild */
    void build(CalendarView cv);
    
    void setEnabled(boolean enable);
    boolean isEnabled();
}



