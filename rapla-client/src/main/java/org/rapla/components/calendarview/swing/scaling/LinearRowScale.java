package org.rapla.components.calendarview.swing.scaling;

import org.rapla.components.util.DateTools;

import java.util.Date;



public class LinearRowScale implements IRowScale
{
    private int rowSize = 15;
    private int rowsPerHour = 4;
    final private static int MINUTES_PER_HOUR= 60;

    private int mintime;
    private int maxtime;
    private int workstartMinutes;
    private int workendMinutes;
    private int offsetMinutes;
    
    public LinearRowScale()
    {
    }

    @Override
    public void setOffsetMinutes(int offsetMinutes)
    {
        this.offsetMinutes = offsetMinutes;
    }

    public int getOffsetMinutes()
    {
        return offsetMinutes;
    }

    public int getRowsPerDay()
    {
        return rowsPerHour * 24;
    }

    public void setRowSize( int rowSize )
    {
        this.rowSize = rowSize;
    }

    public int getRowSize()
    {
        return rowSize;
    }

    public int getRowsPerHour()
    {
        return rowsPerHour;
    }

    public void setRowsPerHour( int rowsPerHour )
    {
        this.rowsPerHour = rowsPerHour;
    }
    
    public int calcHour(int index) {
        int i = (index / rowsPerHour) + getOffsetMinutes()/ 60;
        if ( i>24)
        {
            i-=24;
        }
        return i;
    }

    public int calcMinute(int index) {
        double minutesPerRow =  60. / rowsPerHour;
        long minute = Math.round((index % rowsPerHour) * (minutesPerRow));
		return (int)minute;
    }

    public int getSizeInPixel()
    {
        return rowSize * getMaxRows();
    }
    
    public int getMaxRows()
    {
        int max;
        max = (rowsPerHour * (maxtime - mintime)) / 60 ;
        return max;
    }
    
    private int getMinuteOfDay(Date time) {
       return (DateTools.getMinuteOfDay(time.getTime()));
   }

   public int getYCoord(Date time)  {
       int diff = getMinuteOfDay(time) - mintime  ;
       int pixelPerHour= rowSize * rowsPerHour;
       return (diff * pixelPerHour) / MINUTES_PER_HOUR;
   }
   
   public int getStartWorktimePixel()
   {
       int pixelPerHour= rowSize * rowsPerHour;
       int diff = normalizeHour(workstartMinutes - offsetMinutes);
       int starty = (int) (pixelPerHour * diff   / 60.);
       return starty;   
   }


    public int getEndWorktimePixel()
   {
       int pixelPerHour= rowSize * rowsPerHour;
       int diff = normalizeHour(workendMinutes - offsetMinutes);
       int endy = (int) (pixelPerHour * diff / 60.);
       return endy;
   }

    private int normalizeHour(int diff)
    {
        if ( diff < 0)
        {
            diff += 24*60;
        }
        if ( diff > 24*60)
        {
            diff -=24*60;
        }
        return diff;
    }


    public boolean isPaintRowThick( int row )
    {
        return  row % rowsPerHour == 0;
    }

    public void setTimeIntervall( int startMinutes, int endMinutes )
    {
        mintime = startMinutes;
        maxtime = endMinutes;
    }

    public void setWorktimeMinutes( int workstartMinutes, int workendMinutes )
    {
    	this.workstartMinutes = workstartMinutes;
        this.workendMinutes = workendMinutes;
    }

    public int getYCoordForRow( int row )
    {
        return row * rowSize;
    }

    public int getSizeInPixelBetween( int startRow, int endRow )
    {
        return (endRow - startRow) * rowSize;
    }

    public int getRowSizeForRow( int row )
    {
        return rowSize;
    }
    
    public int calcRow(int y) {
        int rowsPerDay = getRowsPerDay();
        int row = (y-3) / rowSize;
        return Math.min(Math.max(0, row), rowsPerDay -1);
    }
    
    public int trim(int y )
    {
        return (y  / rowSize) * rowSize;
    }

    public int getDraggingCorrection( int y)
    {
        return rowSize / 2;
    }



}
