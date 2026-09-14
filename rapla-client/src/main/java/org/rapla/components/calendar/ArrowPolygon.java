/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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

package org.rapla.components.calendar;
import java.awt.Polygon;

/*
 The following classes are only responsible to paint the Arrows for the NavButton.
*/

class ArrowPolygon extends Polygon {
    private static final long serialVersionUID = 1L;

    public ArrowPolygon(char type,int width) {
        this(type,width,true);
    }

    public ArrowPolygon(char type,int width,boolean border) {
        int polyWidth = ((width - 7) / 8);
        polyWidth = (polyWidth + 1) * 8;
        int dif = border ? (width - polyWidth) /2  : 0;
        int half = polyWidth / 2 + dif;
        int insets = (polyWidth == 8) ? 0 : polyWidth / 4;
        int start = insets + dif;
        int full = polyWidth - insets + dif;
        int size = (polyWidth == 8) ? polyWidth / 4 : polyWidth / 8;

        if ( type == '>') {
            addPoint(half - size,start);
            addPoint(half + size,half);
            addPoint(half - size,full);
        } // end of if ()

        if ( type == '<') {
            addPoint(half + size,start);
            addPoint(half - size,half);
            addPoint(half + size,full);
        } // end of if ()

        if ( type == '^') {
            addPoint(start,half + size);
            addPoint(half,half - size);
            addPoint(full,half + size);
        } // end of if ()

        if ( type == 'v') {
            addPoint(start,half - size);
            addPoint(half,half + size);
            addPoint(full,half - size);
        } // end of if ()

        if ( type == '-') {
            addPoint(start + 1,half - 1);
            addPoint(full - 3,half - 1);
        } // end of if ()

        if ( type == '+') {
            addPoint(start + 1,half - 1);
            addPoint(full - 3,half - 1);
            addPoint(half -1,half -  1);
            addPoint(half- 1, half - (size + 1));
            addPoint(half -1,half + ( size -1));
            addPoint(half -1,half - 1);
        } // end of if ()

    }
}

