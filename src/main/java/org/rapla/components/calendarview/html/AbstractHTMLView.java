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

package org.rapla.components.calendarview.html;

import org.rapla.components.calendarview.AbstractCalendar;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.BlockComparator;
import org.rapla.components.calendarview.CalendarView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

public abstract class AbstractHTMLView extends AbstractCalendar implements CalendarView {
    public static String COLOR_NO_RESOURCE = "#BBEEBB";
   
    String m_html;
   
    abstract public Collection<Block> getBlocks();

    protected void checkBlock( Block bl ) {
        Date endDate = getEndDate();
		if ( !bl.getStart().before(endDate)) {
            throw new IllegalStateException("Start-date " +bl.getStart() + " must be before calendar end at " +endDate);
        }
    }
   

    public String getHtml() {
        return m_html;    
    }
    
    
    protected class HTMLSmallDaySlot extends ArrayList<Block> {
        private static final long serialVersionUID = 1L;

        private String date;
        private Date startTime;
        public HTMLSmallDaySlot(String date) {
            super(2);
            this.date = date;
        }
        public void putBlock(Block block) {
            add( block );
        }
        public void sort() {
            Collections.sort( this, BlockComparator.COMPARATOR);
        }
        
        public void paint(StringBuffer out) {
            out.append("<div valign=\"top\" align=\"right\">");
            out.append( date );
            out.append("</div>\n");
            for ( int i=0;i<size();i++) {
                Block block =  get(i);
                out.append("<div valign=\"top\" class=\"month_block\"");
                if ( block instanceof HTMLBlock ) {
                    out.append(" style=\"background-color:" + ((HTMLBlock)block).getBackgroundColor() + ";\"");
                }
                out.append(">");
                out.append(block.toString());
                out.append("</div>\n");
            }
        }
        
        public void setStart(Date date) 
        {
        	startTime = date;
    	}
        
        public Date getStart() 
        {
        	return startTime;
    	}
    }



        
}
