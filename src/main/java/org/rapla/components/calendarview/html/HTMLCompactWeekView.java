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

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.Builder.PreperationResult;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.AppointmentBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HTMLCompactWeekView extends AbstractHTMLView {
    public final static int ROWS = 6; //without the header row
    /** shared calendar instance. Only used for temporary stored values. */
    HTMLSmallDaySlot[] slots = {};
    String[] slotNames = {}; 
    private ArrayList<List<Block>> rows = new ArrayList<>();
    Map<Block, Integer> columnMap = new HashMap<>();
	private double leftColumnSize = 0.1;
    String weeknumber = "";
    
    public String getWeeknumber() {
		return weeknumber;
	}

	public void setWeeknumber(String weeknumber) {
		this.weeknumber = weeknumber;
	}

	public void setLeftColumnSize(double leftColumnSize) {
		this.leftColumnSize = leftColumnSize;
	}

	public void setSlots( String[] slotNames ) {
        this.slotNames = slotNames;
    }
    
    public Collection<Block> getBlocks() {
        ArrayList<Block> list = new ArrayList<>();
        for (int i=0;i<slots.length;i++) {
            list.addAll(slots[i]);
        }
        return Collections.unmodifiableCollection( list );
    }
    
    /** must be called after the slots are filled*/
    protected boolean isEmpty( int column) 
    {
    	HTMLSmallDaySlot slot = slots[column];
		return slot.isEmpty();
    }

    
    public void rebuild(Builder b) {
    	List<String> headerNames;
    	int columns = getColumnCount();
		headerNames = getHeaderNames();
        columnMap.clear();
        rows.clear();
        for ( int i=0; i<slotNames.length; i++ ) {
            addRow();
        }
        // calculate the blocks
        PreperationResult prep = b.prepareBuild(getStartDate(),getEndDate());
        
        // build Blocks
        final Date startDate = this.getStartDate();
        final Collection<AppointmentBlock> appointmentBlocks = prep.getBlocks();
        b.build(this, startDate, appointmentBlocks);
        
        // resource header

        // add headers
        StringBuffer result = new StringBuffer();
        result.append("<table class=\"month_table\">\n");
        result.append("<tr>\n");
        
        result.append("<th class=\"week_number\" width=\"" + Math.round(getLeftColumnSize() * 100) + "%\">");
        result.append(weeknumber);
        result.append("</th>");
        String percentage = "" + Math.round(95.0 / (Math.max(0, columns)));
         
        int startRow = 0;
        int rowsize = rows.size();
        rowsize -= startRow ;


        slots = new HTMLSmallDaySlot[rowsize * columns];
        for (int row=0;row<rowsize;row++) {
            List<Block> blocks =  rows.get( row + startRow );
            for (int column=0;column < columns; column++) {
                int fieldNumber = row * columns + column;
                slots[fieldNumber] = createField( blocks, column );
            }
        }

        for (int i=0;i<columns;i++) {
            if (isExcluded(i)) {
                continue;
            }
            result.append("<td class=\"month_header\" width=\""+percentage + "%\">");
            result.append("<nobr>");
            result.append(headerNames.get(i));
            result.append("</nobr>");
            result.append("</td>");
        }
        result.append("\n</tr>");
        
        for (int row=0;row<rowsize;row++) {
            result.append("<tr>\n");
            result.append("<th class=\"month_rowheader\" valign=\"top\" height=\"40\">\n");
            if ( slotNames.length > row ) {
                result.append( slotNames[ row ] );
            }
            result.append("</th>\n");
            for (int column=0;column < columns; column++) {
                int fieldNumber = row * columns + column;
                if ( !isExcluded( column ) ) {
                    result.append("<td class=\"month_cell\" valign=\"top\" height=\"40\">\n");
                    slots[fieldNumber].paint( result );
                    result.append("</td>\n");
                }
            }
            result.append("</tr>\n");
        }
        result.append("</table>");
        m_html = result.toString();
    }

	protected List<String> getHeaderNames() {
		List<String> headerNames = new ArrayList<>();
        Date date = getStartDate();
        int columnCount = getColumnCount();
		for (int i=0;i<columnCount;i++) {
            headerNames.add (getRaplaLocale().formatDayOfWeekDateMonth(date ));
            date = DateTools.addDay( date);
        }
		return headerNames;
	}

    public double getLeftColumnSize() {
		return leftColumnSize ;
	}

	protected int getColumnCount()
    {
		return getDaysInView();
	}

	private HTMLSmallDaySlot createField(List<Block> blocks, int column)  {
        HTMLSmallDaySlot c = new HTMLSmallDaySlot("");
        c.setStart(DateTools.addDays( getStartDate(), column));
        if ( blocks != null) {
            Iterator<Block> it = blocks.iterator();
            while (it.hasNext()){
                HTMLBlock block = (HTMLBlock)it.next();
                final Integer integer = columnMap.get(block);
                if (integer == column) {
            		c.putBlock( block );
                }
            }
        }
        c.sort();
        return c;
    }

    
    public void addBlock(Block block, int column,int slot) {
        checkBlock( block );
        while ( rows.size() <= slot ) {
            addRow();
        }
        List<Block> blocks =  rows.get( slot );
        blocks.add( block );
        columnMap.put(block, column);
    }

    private void addRow() {
        rows.add( rows.size(), new ArrayList<>());
    }

    
}
