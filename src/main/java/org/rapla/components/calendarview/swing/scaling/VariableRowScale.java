package org.rapla.components.calendarview.swing.scaling;

import org.rapla.components.util.DateTools;

import java.util.Date;



public class VariableRowScale implements IRowScale
{
    PeriodRow[] periodRows;
    private final int hourSize = 60;
    final private static int MINUTES_PER_HOUR= 60;
    
    private final int mintime =0 ;
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
       return DateTools.getMinuteOfDay(time.getTime());
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

    public boolean isPaintRowThick( int row )
    {
        return  true;
    }

    @Override
    public void setOffsetMinutes(int offsetMinutes)
    {

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
