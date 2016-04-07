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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.Builder.PreperationResult;
import org.rapla.components.util.DateTools;

public class HTMLMonthView extends AbstractHTMLView {
    public final static int ROWS = 6; //without the header row
    public final static int COLUMNS = 7;
    HTMLSmallDaySlot[] slots;
    int offset = 0;
    
    public Collection<Block> getBlocks() {
        ArrayList<Block> list = new ArrayList<Block>();
        for (int i=0;i<slots.length;i++) {
            list.addAll(slots[i]);
        }
        return Collections.unmodifiableCollection( list );
    }
    
    protected boolean isEmpty(int column) {
        for ( int i=column;i < slots.length;i+=7 ) {
            int j = i - offset;
            if (j<0)
            {
                j+=7;
            }
            final HTMLSmallDaySlot slot = slots[j];
            final boolean empty = slot.isEmpty();
            if (!empty ) {
                return false;
            }
        }
        return true;
    }
    
    public void rebuild(Builder b) {
        //      we need to clone the calendar, because we modify the calendar object int the getExclude() method 
        //Calendar counter = (Calendar) blockCalendar.clone(); 
        
        // calculate the blocks
        final Date startDate = getStartDate();
        Date counter = startDate;
        int firstDayOfWeek = getFirstWeekday();
        if ( DateTools.getWeekday(counter) != firstDayOfWeek)
        {
            counter = DateTools.getFirstWeekday(counter, firstDayOfWeek);
            if ( counter.after( startDate))
            {
                counter = DateTools.addDays( counter,  -7);
            }
        }
        Date time = counter;
        offset = (int) DateTools.countDays(counter,startDate);
        PreperationResult prep = b.prepareBuild(startDate,getEndDate());
        slots = new HTMLSmallDaySlot[ daysInMonth ];
        for (int i=0;i<slots.length;i++) {
            slots[i] = new HTMLSmallDaySlot(String.valueOf( i + 1));
        }
        
        b.build(this, prep.getBlocks());
        int lastRow = 0;
        HTMLSmallDaySlot[][] table = new HTMLSmallDaySlot[ROWS][COLUMNS];
        
        // add headers
     
	    counter = startDate;
        for (int i=0; i<daysInMonth; i++) {
            int column = (offset + i) % 7;
            int row = (DateTools.getDayOfMonth(counter) + 6 - column ) /  7;
            final HTMLSmallDaySlot slot = slots[i];
            slot.sort();
            table[row][column] = slot;
            lastRow = row;
            counter = DateTools.addDays(counter,1);
        }
        
        StringBuffer result = new StringBuffer();
        
		// Rapla 1.4: Show month and year in monthview
		
		result.append("<h2 class=\"title\">" + getRaplaLocale().formatMonthYear( startDate ) + "</h2>\n");
        
        result.append("<table class=\"month_table\">\n");
        result.append("<tr>\n");

        counter = time ;
        for (int i=0;i<COLUMNS;i++) {
            if (isExcluded(i)) {
                counter = DateTools.addDays(counter,1);
            	continue;
            }

            int weekday = DateTools.getWeekday(counter);
        	if ( counter.equals( startDate))
        	{
        		offset = i;
        	}
            result.append("<td class=\"month_header\" width=\"14%\">");
            result.append("<nobr>");
            String name = getRaplaLocale().getWeekdayName(weekday);
            result.append(name);
            result.append("</nobr>");
            result.append("</td>");
            counter = DateTools.addDays(counter,1);
        }
        result.append("\n</tr>");
        
        for (int row=0; row<=lastRow; row++) {
            boolean excludeRow = true;
            // calculate if we can exclude the row
            for (int column = 0; column<COLUMNS; column ++) {
                if ( table[row][column] != null && !isExcluded( column )) {
                    excludeRow = false;
                }
            }
            if ( excludeRow )
                continue;
            result.append("<tr>\n");
            for (int column = 0; column<COLUMNS; column ++) {
                if ( isExcluded( column )) {
                    continue;
                }
                HTMLSmallDaySlot slot = table[row][column];
                if ( slot == null ) {
                    result.append("<td class=\"month_cell\" height=\"40\"></td>\n");
                } else {
                    result.append("<td class=\"month_cell\" valign=\"top\" height=\"40\">\n");
                    slot.paint( result );
                    result.append("</td>\n");
                }
            }
            result.append("</tr>\n");
        }
        result.append("</table>");
        m_html = result.toString();
    }

    public void addBlock(Block block,int col,int slot) {
        checkBlock( block );
        int day = DateTools.getDayOfMonth(block.getStart());
        slots[day-1].putBlock( block );
    }
   

}
