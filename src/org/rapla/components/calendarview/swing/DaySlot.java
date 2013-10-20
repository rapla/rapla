/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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
package org.rapla.components.calendarview.swing;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;

public interface DaySlot {
   void paintDraggingGrid(int x,int y, int height,SwingBlock block, int oldY,int oldHeight,boolean bPaint);
    Point getLocation();
    void unselectAll();
    int calcRow(int y);
    int calcSlot(int x);
    int getX(Component component);
    void select(int startRow,int endRow);
    void setCursor(Cursor cursor);
}
