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

import org.rapla.entities.domain.AppointmentBlock;

import java.util.Collection;
import java.util.Date;

public interface Builder {
   /** Calculate the blocks that should be displayed in the weekview.
    * This method should not be called manually.
    * It is called by the CalendarView during the build process.
    * @see #build
    * @param start
    * @param end
    */
    PreperationResult prepareBuild(Date start, Date end);

   /** {@code LocalDateTime} variant. */
    default PreperationResult prepareBuild(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return prepareBuild(
            start == null ? null : org.rapla.components.util.DateTools.toDate(start),
            end == null ? null : org.rapla.components.util.DateTools.toDate(end));
    }
   

        class PreperationResult
    {
        final int minMinutes;
        final int maxMinutes;
        final Collection<AppointmentBlock> blocks;
        public PreperationResult(int min, int max,Collection<AppointmentBlock> blocks )
        {
            this.minMinutes = min;
            this.maxMinutes = max;
            this.blocks = blocks;
        }
        /** The maximal ending-time of all blocks that should be displayed.
        Call prepareBuild first to calculate the blocks.*/
        public int getMaxMinutes()
        {
            return maxMinutes;
        }
        /** The minimal starting-time of all blocks that should be displayed.
       Call prepareBuild first to calculate the blocks.*/
        public int getMinMinutes()
        {
            return minMinutes;
        }
        
        public Collection<AppointmentBlock> getBlocks()
        {
            return blocks;
        }

    }
    
   
    /** Build the calculated blocks into the weekview. This method should not be called manually.
     * It is called by the CalendarView during the build process.
     * @see #prepareBuild */
    void build(BlockContainer blockContainer,Date startDate,Collection<AppointmentBlock> blocks);

    /** {@code LocalDate} variant — distinct method name avoids `null`-passing ambiguity. */
    default void buildLocalDate(BlockContainer blockContainer, java.time.LocalDate startDate, Collection<AppointmentBlock> blocks) {
        build(blockContainer, startDate == null ? null : org.rapla.components.util.DateTools.toDate(startDate), blocks);
    }
}



