/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JComponent;

import org.rapla.framework.RaplaLocale;

/** A vertical scale displaying the hours of day. Uses am/pm notation
 * in the appropriate locale.
*/
public class TimeScale extends JComponent
{
    private static final long serialVersionUID = 1L;

    private int pixelPerHour = 60;
    private int mintime;
    private int maxtime;
    private boolean useAM_PM = false;
    private Font fontLarge= new Font("SansSerif", Font.PLAIN, 14);
    private Font fontSmall= new Font("SansSerif", Font.PLAIN, 9);
    private FontMetrics fm1 = getFontMetrics(fontLarge);
    private FontMetrics fm2 = getFontMetrics(fontSmall);
    String[] hours;
    private int SCALE_WIDTH = 35;
    private boolean smallSize = false;
    private int repeat = 1;
    private String days[] ;

    
    public TimeScale() {
        useAM_PM = false;
    }
    
    public void setLocale(RaplaLocale locale) {
        if (locale == null)
            return;
        useAM_PM = locale.isAmPmFormat();
        createHours(locale);
    }

    /**
       mintime und maxtime definieren das zeitintevall in vollen stunden.
       die skalen-einteilung wird um vgap pixel nach unten verschoben
       (um ggf. zu justieren).
    */
    public void setTimeIntervall(int minHour, int maxHour, int pixelPerHour) {
        removeAll();
        this.mintime = minHour;
        this.maxtime = maxHour;
        this.pixelPerHour = pixelPerHour;
        //setBackground(Color.yellow);
        //super(JSeparator.VERTICAL);
        setLayout(null);
        setPreferredSize(new Dimension( SCALE_WIDTH, (maxHour-minHour + 1) * pixelPerHour * repeat));
    }

    private void createHours(RaplaLocale locale) {
        hours = new String[24];
        for (int i=0;i<24;i++) {
            hours[i] = locale.formatHour( i);
        }
    }


    public void setSmallSize(boolean smallSize) {
        this.smallSize = smallSize;
    }

    public void setRepeat(int repeat, String[] days) {
        this.repeat = repeat;
        this.days = days;
     }

     public void paint(Graphics g)  {
        super.paint(g);
        int indent[];
        int heightHour = (int) fm1.getLineMetrics("12",g).getHeight() ;
        int heightEnding = (int) fm2.getLineMetrics("12",g).getHeight() ;
        int current_y ;

        // Compute indentations
        FontMetrics fm;
        String[] indent_string = new String[3] ;
        if ( days != null ) {
            indent_string[0] = "M";
            indent_string[1] = "M2";
            indent_string[2] = "M22";
        } else {
            indent_string[0] = "";
            indent_string[1] = "2";
            indent_string[2] = "22";
        }
        if ( smallSize ) {
            fm = fm2;
        } else {
            fm = fm1;
        }
        
        indent = new int[3];
        for(int i=0; i<3; i++) {
            indent[i] = fm.stringWidth(indent_string[i]) ;
        }

        Rectangle rect = g.getClipBounds();
        //System.out.println(mintime + " - " + maxtime);
        int height = (maxtime - mintime) * pixelPerHour + 1 ;
        
        if ( days != null ) {
            g.drawLine(indent[0]+1,0,indent[0]+1,repeat*height);
        }
        
        for (int r=0; r<repeat; r++) {
            current_y = height * r;
            g.drawLine(0,current_y-1,SCALE_WIDTH ,current_y-1);
            int pad = 0;
            if ( days != null ) {
                pad =  (maxtime - mintime - days[r].length())/2 ;
                if ( pad < 0 ) {
                    pad = 0;
                }
            }
            for (int i=mintime; i<maxtime; i++) {
                int y = current_y + (i - mintime) * pixelPerHour;
                int hour;
                String ending;
                String prefix;
                if (useAM_PM) {
                    hour = (i == 0) ? 12 : ((i-1)%12 + 1);
                    ending = (i<=11) ?  "AM" : "PM";
                }  else {
                    hour = i;
                    ending = "00";
                }
    
                if ( days != null && i - mintime < days[r].length() + pad  && i - mintime >= pad  ) {
                    prefix = days[r].substring(i-mintime-pad,i-mintime+1-pad);
                } else {
                    prefix = null;
                }
                
                if (y  >= rect.y && y <= (rect.y + rect.height)) {
                    g.drawLine(i == mintime ? 0:indent[0]+1,y,SCALE_WIDTH ,y);
                }

                // Uncommenting this draws the last line 
//                if (y  >= rect.y && y <= (rect.y + rect.height) && i == maxtime-1) {
//                    g.drawLine(0,y+pixelPerHour,SCALE_WIDTH ,y+pixelPerHour);
//                }
                if (y  >= rect.y -heightHour && y <= (rect.y + rect.height) + heightHour ) {
                    if ( smallSize ) {
                        g.setFont(fontSmall);
                    } else {
                        g.setFont(fontLarge);
                    }
                    if ( prefix != null ) {           
                        g.drawString(prefix, (indent[0]-fm.stringWidth(prefix)+1)/2,y + heightEnding);
                    }
                    g.drawString(hours[i],(hour < 10) ? indent[1]+2:indent[0]+2,y + ( smallSize ? heightEnding : heightHour));
                    if ( !smallSize ) {
                        g.setFont(fontSmall);
                    }
                    g.drawString(ending, indent[2]+2,y + heightEnding);
                }
            }
        }
    }
}

