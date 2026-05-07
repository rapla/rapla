package org.rapla.components.calendarview.swing.scaling;

import java.util.Date;



public interface IRowScale extends IRowScaleSmall
{
     int calcHour( int index );
     int calcMinute( int index );
     int getSizeInPixel();
     int getMaxRows();
     int getRowsPerDay();
     int getYCoord( Date time );

     /** {@code LocalTime} variant — distinct name. */
     default int getYCoord(java.time.LocalTime time) {
         return getYCoord(time == null ? null : new Date(time.getHour() * 3600_000L + time.getMinute() * 60_000L + time.getSecond() * 1000L));
     }
     int getStartWorktimePixel();
     int getEndWorktimePixel();
     int getSizeInPixelBetween( int startRow, int endRow );
     
     boolean isPaintRowThick( int row );

    void setOffsetMinutes(int offsetMinutes);
}