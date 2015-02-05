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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;

/** Graphical component for displaying a calendar like monthview.
 *
*/
public class SwingMonthView extends AbstractSwingCalendar
{
    public final static int ROWS = 6; //without the header row
    public final static int COLUMNS = 7;
    private SmallDaySlot[] slots;
    DraggingHandler draggingHandler = new DraggingHandler(this, false);
    SelectionHandler selectionHandler = new SelectionHandler(this);
    JLabel monthTitle = new JLabel();

    public SwingMonthView() {
        this(true);
    }

    public SwingMonthView(boolean showScrollPane) {
        super( showScrollPane );
        monthTitle.setOpaque( false);
        jTitlePanel.add(monthTitle,  BorderLayout.NORTH);
        monthTitle.setHorizontalAlignment( JLabel.CENTER);
        monthTitle.setFont(monthTitle.getFont().deriveFont(Font.BOLD));
       
    }

    protected boolean isEmpty(int column) {
        for ( int i=column;i < slots.length;i+=7 ) {
            if (!slots[i].isEmpty() ) {
                return false;
            }
        }
        return true;
    }
    
    public Collection<Block> getBlocks(int dayOfMonth) {
        int index = dayOfMonth-1;
        return Collections.unmodifiableCollection(slots[ index ].getBlocks());
    }

    public Collection<Block> getBlocks() {
        ArrayList<Block> list = new ArrayList<Block>();
        for (int i=0;i<slots.length;i++) {
            list.addAll(slots[i].getBlocks());
        }
        return Collections.unmodifiableCollection( list );
    }

    public void setEditable(boolean b) {
        super.setEditable( b);
        if ( slots == null )
            return;
        // Hide the rest
        for (int i= 0;i<slots.length;i++) {
            SmallDaySlot slot = slots[i];
            if (slot == null) continue;
            slot.setEditable(b);
        }
    }
    
    TableLayout tableLayout;

    public void rebuild() {
        // we need to clone the calendar, because we modify the calendar object in the getExclude() method 
        Calendar counter = createCalendar(); 
        Iterator<Builder> it= builders.iterator();
        Date startDate = getStartDate();
		while (it.hasNext()) {
            Builder b= it.next();
            b.prepareBuild(startDate,getEndDate() );
        }
        
        // create fields
        slots = new SmallDaySlot[daysInMonth];
        counter.setTime(startDate);
        int year = counter.get(Calendar.YEAR);
        SimpleDateFormat format = new SimpleDateFormat("MMMMMM",locale);
        format.setTimeZone( getTimeZone() );
        String monthname = format.format(counter.getTime());
        // calculate the blocks
        for (int i=0; i<daysInMonth; i++) {
            createField(i, counter.getTime());
            counter.add(Calendar.DATE,1);
        }
        // clear everything
        jHeader.removeAll();
        jCenter.removeAll();
       
        monthTitle.setText( monthname + " " + year);
        // build Blocks
        it= builders.iterator();
        while (it.hasNext()) {
            Builder b= it.next();
            if (b.isEnabled()) { b.build(this); }
        }
        tableLayout= new TableLayout();
        jCenter.setLayout(tableLayout);
        counter.setTime(startDate);
        int firstDayOfWeek = getFirstWeekday();
		if ( counter.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek)
        {
			counter.set(Calendar.DAY_OF_WEEK, firstDayOfWeek);
			if ( counter.getTime().after( startDate))
			{
				counter.add(Calendar.DATE, -7);
			}
        }
        // add headers
        int offset = (int) DateTools.countDays(counter.getTime(),startDate);
        for (int i=0;i<COLUMNS;i++) {
        	int weekday = counter.get(Calendar.DAY_OF_WEEK);
        	if ( !isExcluded(i) ) {
                tableLayout.insertColumn(i, slotSize );
                jHeader.add( createSlotHeader( weekday ) );
            } else {
                tableLayout.insertColumn(i, 0);
            }
            counter.add(Calendar.DATE,1);
        }
        for (int i=0;i<ROWS;i++) {
            tableLayout.insertRow(i, TableLayout.PREFERRED );
        }
        // add Fields
        counter.setTime(startDate);
        for (int i=0; i<daysInMonth; i++) {
            int column = (offset + i) % 7;
            int row = (counter.get(Calendar.DATE) + 6 - column ) /  7;
            if ( !isExcluded( column ) ) {
                jCenter.add( slots[i] , "" + column + "," + row);
            }
            counter.add(Calendar.DATE,1);
        }
        selectionHandler.clearSelection();
        jHeader.validate();
        jCenter.validate();
    	if ( isEditable())
        {
        	updateSize(component.getSize().width);
        }
        component.revalidate();
        component.repaint();
    }

    private void createField(int pos, Date date)  {
        slots[pos]= createSmallslot(pos, date);
    }

    protected SmallDaySlot createSmallslot(int pos, Date date)  {
        String headerText = "" + (pos + 1);
		Color headerColor = getNumberColor( date);
		Color headerBackground = null;
		return createSmallslot(headerText, headerColor,headerBackground);
    }
    
    protected SmallDaySlot createSmallslot(String headerText, Color headerColor,
			Color headerBackground) 
    {
    	return createSmallslot(headerText, slotSize,headerColor,headerBackground);
	}

	protected SmallDaySlot createSmallslot(String headerText, int width, Color headerColor, Color headerBackground )  
    {
		SmallDaySlot c= new SmallDaySlot(headerText, width, headerColor, headerBackground);
        c.setEditable(isEditable());
        c.setDraggingHandler(draggingHandler);
        c.addMouseListener(selectionHandler);
        c.addMouseMotionListener(selectionHandler);
        return c;
    }
    
    public static Color DATE_NUMBER_COLOR = Color.gray;
    
    /**
     * @param date  
     */
    protected Color getNumberColor( Date date)
    {
        return DATE_NUMBER_COLOR;
    }

    /** override this method, if you want to create your own header. */
    protected JComponent createSlotHeader(int weekday) {
        JLabel jLabel = new JLabel();
        jLabel.setBorder(isEditable() ? SLOTHEADER_BORDER : null);
        jLabel.setText( getWeekdayName(weekday) );
        jLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        jLabel.setHorizontalAlignment(JLabel.CENTER);
        jLabel.setOpaque(false);
        jLabel.setForeground(Color.black);
        Dimension dim = new Dimension(this.slotSize,20);
        jLabel.setPreferredSize( dim);
        jLabel.setMinimumSize( dim ); 
        jLabel.setMaximumSize( dim );
        return jLabel;
    }

   
	public void addBlock(Block bl, int col,int slot) {
        checkBlock( bl );
  //      System.out.println("Put " + bl.getStart() + " into field " + (date -1));
        slots[col].putBlock((SwingBlock)bl);
    }
    
    public int getSlotNr( DaySlot slot) {
        for (int i=0;i<slots.length;i++)
            if (slots[i] == slot)
                return i;
        throw new IllegalStateException("Slot not found in List");
    }
    
    public boolean isSelected(int nr)
	 {
		 SmallDaySlot slot = getSlot(nr);
		 if ( slot == null)
		 {
			 return false;
		 }
		 return slot.isSelected();
	}

    int getRowsPerDay() {
        return 1;
    }
    
	 SmallDaySlot getSlot(int nr) {
        if ( nr >=0 && nr< slots.length)    
            return slots[nr];
        else
            return null;
    }
    
    int getDayCount() {
        return slots.length;
    }
    
    int calcSlotNr(int x, int y) {
        for (int i=0;i<slots.length;i++) {
            if (slots[i] == null)
                continue;
            Point p = slots[i].getLocation();
            if ((p.x <= x) 
                && (x <= p.x + slots[i].getWidth())  
                && (p.y <= y)
                && (y <= p.y + slots[i].getHeight())
            ) {
                return i;
            }
        }
        return -1;
    }

    SmallDaySlot calcSlot(int x,int y) {
        int nr = calcSlotNr(x, y);
        if (nr == -1) {
            return null;
        } else {
            return slots[nr];
        }
    }
    
    Date createDate(DaySlot slot, int row, boolean startOfRow) {
        Calendar calendar = createCalendar();
        calendar.setTime( getStartDate() );
        int dayOfMonth = getSlotNr( slot ) +1;
        calendar.set( Calendar.DAY_OF_MONTH, dayOfMonth);
        if ( !startOfRow ) {
            calendar.add( Calendar.DATE , 1 );
        }
        calendar.set( Calendar.HOUR_OF_DAY, 0 );
        calendar.set( Calendar.MINUTE, 0 );
        calendar.set( Calendar.SECOND, 0 );
        calendar.set( Calendar.MILLISECOND, 0 );
        return calendar.getTime();
    }

	@Override
	public void updateSize(int width) {
		int columnSize = tableLayout.getNumColumn();
		int newWidth = Math.max( minBlockWidth , (width - 10 ) /  (Math.max(1,columnSize)));
		for (SmallDaySlot slot: this.slots)
    	{
    		if ( slot != null)
    		{
    			slot.updateSize(newWidth);
    		}
    	}
		setSlotSize(newWidth);
		for ( int i=0;i< columnSize;i++)
		{
			tableLayout.setColumn(i, newWidth);
		}
		for (Component comp:jHeader.getComponents())
		{
			double height = comp.getPreferredSize().getHeight();
			Dimension dim = new Dimension( newWidth,(int) height);
			comp.setPreferredSize(dim);
			comp.setMaximumSize(dim);
			comp.setMinimumSize( dim);
			comp.invalidate();
		}
		jCenter.invalidate();
		jCenter.repaint();

	}
   

}


