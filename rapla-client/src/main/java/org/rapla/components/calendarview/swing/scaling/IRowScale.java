package org.rapla.components.calendarview.swing.scaling;

import java.time.LocalDateTime;
public interface IRowScale extends IRowScaleSmall
{
     int calcHour( int index );
     int calcMinute( int index );
     int getSizeInPixel();
     int getMaxRows();
     int getRowsPerDay();
     int getYCoord( LocalDateTime time );

     int getStartWorktimePixel();
     int getEndWorktimePixel();
     int getSizeInPixelBetween( int startRow, int endRow );
     
     boolean isPaintRowThick( int row );

    void setOffsetMinutes(int offsetMinutes);
}