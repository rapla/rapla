/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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
import java.util.Collection;
import java.util.Date;
public interface CalendarView extends BlockContainer
{
    /** returns the first Date that will be displayed in the calendar */
    Date getStartDate();
    /** returns the last Date that will be displayed in the calendar */
    Date getEndDate();
    
    /** sets the calendarview to the selected date*/
    void setToDate(Date weekDate);

    /** This method removes all existing blocks first. 
     * Then it calls the build method of all added builders, so that they can add blocks into the CalendarView again.
     * After all blocks are added the Calendarthat repaints the screen. 
     */
    void rebuild(Builder builder);

    /** returns a collection of all the added blocks
     * @see #addBlock*/
    Collection<Block> getBlocks();
}


