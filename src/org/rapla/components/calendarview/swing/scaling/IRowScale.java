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
     int getStartWorktimePixel();
     int getEndWorktimePixel();
     int getSizeInPixelBetween( int startRow, int endRow );
     
     boolean isPaintRowThick( int row );
}