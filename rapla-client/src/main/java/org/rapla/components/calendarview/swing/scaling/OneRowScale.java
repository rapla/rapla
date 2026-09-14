package org.rapla.components.calendarview.swing.scaling;



public class OneRowScale implements IRowScaleSmall
{
    public int getYCoordForRow( int row )
    {
        return 0;
    }
    
    public int getRowSizeForRow( int row )
    {
        return 15;
    }

    public int calcRow( int y )
    {
        return 0;
    }

    public int trim( int y )
    {
        return 0;
    }

    public int getDraggingCorrection( int y)
    {
       return 15 / 2;
    }

}
