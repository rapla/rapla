package org.rapla.components.calendarview.swing.scaling;


public interface IRowScaleSmall
{
    int getYCoordForRow( int row );
    int getRowSizeForRow( int row );
    int calcRow( int y );
    int trim( int y );
    int getDraggingCorrection(int y);
}