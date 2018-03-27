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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

public class HTMLWeekView extends AbstractHTMLView {
    private int endMinutes;
    private int minMinute;
    private int maxMinute;
    private int startMinutes;
    int m_rowsPerHour = 2;
    HTMLDaySlot[] multSlots ;
    ArrayList<Block> blocks = new ArrayList<>();
    //ArrayList<Integer> blockStart = new ArrayList<Integer>();
    //ArrayList<Integer> blockSize = new ArrayList<Integer>();
    
    String weeknumber;

	/** The granularity of the selection rows.
     * <ul>
     * <li>1:  1 rows per hour =   1 Hour</li>
     * <li>2:  2 rows per hour = 1/2 Hour</li>
     * <li>3:  3 rows per hour = 20 Minutes</li>
     * <li>4:  4 rows per hour = 15 Minutes</li>
     * <li>6:  6 rows per hour = 10 Minutes</li>
     * <li>12: 12 rows per hour =  5 Minutes</li>
     * </ul>
     * Default is 2.
     */
    public void setRowsPerHour(int rows) {
        m_rowsPerHour = rows;
    }

    public int getRowsPerHour() {
        return m_rowsPerHour;
    }

    public void setWorktime(int startHour, int endHour) {
        this.startMinutes = startHour * 60;
        this.endMinutes = endHour * 60;
    }
    
    public void setWorktimeMinutes(int startMinutes, int endMinutes) {
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
    }

    public void setToDate(Date weekDate) {
        calcMinMaxDates( weekDate );
    }

    public Collection<Block> getBlocks() {
        return blocks;
    }

    /** must be called after the slots are filled*/
    protected boolean isEmpty( int column) 
    {
        return multSlots[column].isEmpty();
    }
    
	protected int getColumnCount() 
	{
		return getDaysInView();
	}

    public void rebuild(Builder b) {
        int columns = getColumnCount();
        blocks.clear();
        multSlots = new HTMLDaySlot[columns];
        
        String[] headerNames = new String[columns];
        
        for (int i=0;i<columns;i++) {
        	String headerName = createColumnHeader(i);
			headerNames[i] = headerName;
         }

        // calculate the blocks
        int start = startMinutes;
        int end = endMinutes;
        minuteBlock.clear();
        PreperationResult prep = b.prepareBuild(getStartDate(),getEndDate());
        start = Math.min(prep.getMinMinutes(),start);
        end = Math.max(prep.getMaxMinutes(),end);
        if (start<0)
            throw new IllegalStateException("builder.getMin() is smaller than 0");
        if (end>24*60)
            throw new IllegalStateException("builder.getMax() is greater than 24");
        minMinute = start ;
        maxMinute = end ;
        for (int i=0;i<multSlots.length;i++) {
            multSlots[i] = new HTMLDaySlot(2);
        }

        b.build(this, getStartDate(),prep.getBlocks());
        boolean useAM_PM = getRaplaLocale().isAmPmFormat(  );
        for (int minuteOfDay = minMinute;minuteOfDay<maxMinute;minuteOfDay++) {
            boolean isLine = (minuteOfDay ) % (60 /  m_rowsPerHour) == 0;
            if ( isLine || minuteOfDay == minMinute) {
                minuteBlock.add( minuteOfDay);
            }
        }
 
        buildHtml( headerNames, useAM_PM);
    }

    private void buildHtml( String[] headerNames, boolean useAM_PM) {
        int columns = getColumnCount();
        int firstEventMarkerId = 7;
        StringBuffer result = new StringBuffer();
        result.append("<table class=\"week_table\">\n");
        result.append("<tbody>");
        result.append("<tr>\n");
        result.append("<th class=\"week_number\">"+weeknumber+"</th>");
        for (int i=0;i<multSlots.length;i++) {
            if ( isExcluded ( i ) )
                continue;
            result.append("<td class=\"week_header\" colspan=\""+ (Math.max(1,multSlots[i].size()) * 2 + 1) + "\">");
            result.append("<nobr>");
            result.append(headerNames[i]);
            result.append("</nobr>");
            result.append("</td>");
        }
        result.append("\n</tr>");
        result.append("<tr></tr>");
        boolean firstEventMarkerSet = false;
 
        for (Integer minuteOfDay:minuteBlock) {
        	
        	if ( minuteBlock.last().equals( minuteOfDay))
        	{
        		break;
        	}
            //System.out.println("Start row " + row / m_rowsPerHour  + ":" + row % m_rowsPerHour +" " + timeString );

            result.append("<tr>\n");
            boolean fullHour = (minuteOfDay) % 60 == 0;
			boolean isLine = (minuteOfDay ) % (60 /  m_rowsPerHour) == 0;
            if ( fullHour || minuteOfDay == minMinute) {
            	int rowspan = calcRowspan(minuteOfDay, ((minuteOfDay  / 60) + 1) * 60);
            	String timeString = getRaplaLocale().formatMinuteOfDay(minuteOfDay);
                result.append("<th class=\"week_times\" rowspan=\""+ rowspan  +"\"><nobr>");
                result.append(timeString);
                result.append("</nobr>");
                result.append(" &#160;</th>\n");
            }
         
            
            for (int day=0;day<columns;day++) {
				if (isExcluded(day))
					continue;

				if (multSlots[day].size() == 0)
				{
					// Rapla 1.4: Make line for full hours darker than others
					if (fullHour )
					{
						result.append("<td class=\"week_smallseparatorcell_black\">&nbsp;</td>");
						result.append("<td class=\"week_emptycell_black\">&nbsp;</td>\n");
					}
					else if (isLine)
					{
						result.append("<td class=\"week_smallseparatorcell\">&nbsp;</td>");
						result.append("<td class=\"week_emptycell\">&nbsp;</td>\n");
					}
					else
					{
						result.append("<td class=\"week_smallseparatornolinecell\">&nbsp;</td>");
						result.append("<td class=\"week_emptynolinecell\">&nbsp;</td>\n");
					}
				} 
				else if ( firstEventMarkerSet)
				{
				    firstEventMarkerId = day;
				}
				for (int slotnr = 0; slotnr < multSlots[day].size(); slotnr++)
				{
					// Rapla 1.4: Make line for full hours darker than others
					if (fullHour)
					{
						result.append("<td class=\"week_smallseparatorcell_black\">&nbsp;</td>");
					}
					else if (isLine)
					{
						result.append("<td class=\"week_smallseparatorcell\">&nbsp;</td>");
					}
					else
					{
						result.append("<td class=\"week_smallseparatornolinecell\"></td>");
					}
					
					Slot slot = multSlots[day].getSlotAt(slotnr);
					Block block = slot.getBlock(minuteOfDay);
					if ( block != null)
					{
						long endTime = block.getEnd().getTime();
                        int endMinute = Math.min(maxMinute,DateTools.getMinuteOfDay(endTime));
						int rowspan = calcRowspan(minuteOfDay, endMinute);
						result.append("<td valign=\"top\" class=\"week_block\"");
						result.append(" rowspan=\"" + rowspan + "\"" );
					 	if (block instanceof HTMLBlock)
				            result.append(" style=\"background-color:" + ((HTMLBlock) block).getBackgroundColor() + "\"");
						result.append(">");
						printBlock(result, firstEventMarkerId, block);
						result.append("</td>");
						slot.setLastEnd( endMinute);
					}
					else
					{
						// skip ?
						if (slot.getLastEnd() > minuteOfDay)
						{
							// Do nothing
						}
						else 
						{
							// Rapla 1.4: Make line for full hours darker than others
							if (fullHour )//|| (!slot.isEmpty(row-1) && (row-minRow) > 0))
							{
								result.append("<td class=\"week_emptycell_black\">&nbsp;</td>\n");
							}
							else if (isLine)
							{
								result.append("<td class=\"week_emptycell\">&nbsp;</td>\n");
							}
							else
							{
								result.append("<td class=\"week_emptynolinecell\"></td>\n");
							}
						}

					}
				}
				
				// Rapla 1.4: Make line for full hours darker than others
				if (fullHour)
				{
					result.append("<td class=\"week_separatorcell_black\">&nbsp;</td>");
				}
				else if ( isLine)
				{
					result.append("<td class=\"week_separatorcell\">&nbsp;</td>");
				}
				else
				{
					result.append("<td class=\"week_separatornolinecell\"></td>\n");
				}
			}
			
			result.append("\n</tr>\n");
        }
        result.append("</tbody>");
        result.append("</table>\n");
        m_html = result.toString();
    }

    private int calcRowspan(int start, int end) 
    {
    	if ( start == end)
    	{
    		return 1;
    	}
    	SortedSet<Integer> tailSet = minuteBlock.tailSet( start);
    	int row = 0;
    	for (Integer minute:tailSet)
    	{
    		if ( minute< end)
    		{
    			row++;
    		}
    		else
    		{
    			break;
    		}
    	}
    	return Math.max(1, row);
    }

	public String getWeeknumber() {
		return weeknumber;
	}

	public void setWeeknumber(String weeknumber) {
		this.weeknumber = weeknumber;
	}
	
    protected void printBlock(StringBuffer result, @SuppressWarnings("unused") int firstEventMarkerId, Block block) {
        String string = block.toString();
		result.append(string);
    }


	protected String createColumnHeader(int i) {
		Date date = DateTools.addDays(getStartDate(), i);
		String headerName = getRaplaLocale().formatDayOfWeekDateMonth(date );
		return headerName;
	}

   SortedSet<Integer> minuteBlock = new TreeSet<>();
    
   public void addBlock(Block block,int column,int slot) {
        checkBlock ( block );
        HTMLDaySlot multiSlot =multSlots[column];
        long start = block.getStart().getTime();
        int startMinute =  Math.max(minMinute,(
                DateTools.getHourOfDay( start)* 60
            + DateTools.getMinuteOfHour( start)
            ));
        long end = block.getEnd().getTime();
        int endMinute =  (Math.min(maxMinute,
                DateTools.getHourOfDay( end)* 60
                + DateTools.getMinuteOfHour( end)
            ));
        blocks.add(block);
//        startBlock.add( startMinute);
 //       endBlock.add( endMinute);
        minuteBlock.add( startMinute);
        minuteBlock.add( endMinute);
        multiSlot.putBlock( block, slot, startMinute);
        
    }
   
    protected class HTMLDaySlot extends ArrayList<Slot> {
        private static final long serialVersionUID = 1L;
        private boolean empty = true;

        public HTMLDaySlot(int size) {
            super(size);
        }

        public void putBlock(Block block,int slotnr, int startMinute) {
            while (slotnr >= size()) {
                addSlot();
            }
            getSlotAt(slotnr).putBlock( block, startMinute);
            empty = false;
        }

        public int addSlot() {
            Slot slot = new Slot();
            add(slot);
            return size();
        }
        public Slot getSlotAt(int index) {
            return get(index);
        }

        public boolean isEmpty() {
            return empty;
        }
    }
    
    

    protected class Slot {
//        int[] EMPTY = new int[]{-2};
  //      int[] SKIP = new int[]{-1};
        int lastEnd = 0;
        HashMap<Integer, Block> map = new HashMap<>();
       
		public Slot() {
        }

		public void putBlock( Block block, int startMinute) {
			map.put( startMinute, block);
		}
		
		Block getBlock(Integer startMinute)
		{
			return map.get( startMinute);
		}
       
		public int getLastEnd() {
			return lastEnd;
		}

		public void setLastEnd(int lastEnd) {
			this.lastEnd = lastEnd;
		}


    }
}
