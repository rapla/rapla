package org.rapla.components.calendarview.swing.scaling;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;



public class VariableRowScale implements IRowScale
{
    PeriodRow[] periodRows;
    private int hourSize = 60; 
    final private static int MINUTES_PER_HOUR= 60;
    private TimeZone timeZone = TimeZone.getDefault();

    private int mintime =0 ;
    /*
    private int maxtime = 24;
    private int workstart = 0;
    private int workend = 24;
    */
    class PeriodRow
    {
        int minutes;
        int ypos;
        int startMinute;
        PeriodRow( int minutes )
        {
            this.minutes = minutes;
        }
        
        public int getRowSize()
        {
            return (minutes * hourSize) / 60;
        }
        
    }
    
    public VariableRowScale()
    {
        periodRows = new PeriodRow[] { 
            new PeriodRow( 60 * 8 ),
            new PeriodRow( 45  ),
            new PeriodRow( 60 ),
            new PeriodRow( 10 ),
            new PeriodRow( 110 ),
            new PeriodRow( 60  + 15),
            new PeriodRow( 60 * 3 ),
            new PeriodRow( 60 * 8 )
        };
        
        for ( int i=0;i< periodRows.length-1;i++)
        {
            periodRows[i+1].ypos = periodRows[i].ypos + periodRows[i].getRowSize();
            periodRows[i+1].startMinute = periodRows[i].startMinute + periodRows[i].minutes;
        }
    }
    
    public void setTimeZone( TimeZone timeZone)
    {
        this.timeZone = timeZone;
    }
    
    public int getRowsPerDay()
    {
        return periodRows.length;
    }

        
    
    public int getSizeInPixel()
    {
        PeriodRow lastRow = getLastRow();
        return lastRow.ypos + lastRow.getRowSize();
    }
    
    public int getMaxRows()
    {
       return periodRows.length;
    }
    
    private int getMinuteOfDay(Date time) {
       Calendar cal = getCalendar();
       cal.setTime(time);
       return (cal.get(Calendar.HOUR_OF_DAY )) * MINUTES_PER_HOUR + cal.get(Calendar.MINUTE);
   }

    public int calcHour(int index) {
        return periodRows[index].startMinute / MINUTES_PER_HOUR;
    }

    public int calcMinute(int index) {
        return periodRows[index].startMinute % MINUTES_PER_HOUR;
    }

   public int getYCoord(Date time)  {
       int diff = getMinuteOfDay(time) - mintime * MINUTES_PER_HOUR ;
       return (diff * hourSize) / MINUTES_PER_HOUR;
   }
   
   public int getStartWorktimePixel()
   {
       return periodRows[0].getRowSize();   
   }

   public int getEndWorktimePixel()
   {
       PeriodRow lastRow = getLastRow();
       return lastRow.ypos;   
   }
   
   private PeriodRow getLastRow()
   {
       PeriodRow lastRow = periodRows[periodRows.length-1];
       return lastRow;
   }

 
   private Calendar calendar = null;
   private Calendar getCalendar() {
       // Lazy creation of the calendar
       if (calendar == null)
           calendar = Calendar.getInstance(timeZone);
       return calendar;
   }

    public boolean isPaintRowThick( int row )
    {
        return  true;
    }
/*
    public void setTimeIntervall( int startHour, int endHour )
    {
        mintime = startHour;
        maxtime = endHour;
    }

    public void setWorktime( int startHour, int endHour )
    {
        workstart = startHour;
        workend = endHour;
    }
*/
    public int getYCoordForRow( int row )
    {
        if ( row < 0 || row >= periodRows.length)
        {
            return row* 400;
        }
        return periodRows[row].ypos;
    }

    public int getSizeInPixelBetween( int startRow, int endRow )
    {
        if ( startRow < 0 || endRow < 0 || startRow >= periodRows.length || endRow >=periodRows.length)
        {
            return (endRow - startRow) * 400;
        }
        
        return periodRows[endRow].ypos - periodRows[startRow].ypos;
    }

    public int getRowSizeForRow( int row )
    {
 /*       if ( row < 0 || row >= periodRows.length)
        {
            return 60;
        }
   */ 
        return periodRows[row].getRowSize();
    }
    
    public int calcRow(int y) {
        for ( int i=0;i< periodRows.length;i++)
        {
            PeriodRow row =periodRows[i]; 
            if (row.ypos +  row.getRowSize() >=y)
                return i;
        }
        return 0;
    }
    
    public int trim(int y )
    {
        int row = calcRow( y );
        int rowSize = getRowSizeForRow( row);
        return (y  / rowSize) * rowSize;
    }

    public int getDraggingCorrection(int y)
    {
        return 0;
    }



}
