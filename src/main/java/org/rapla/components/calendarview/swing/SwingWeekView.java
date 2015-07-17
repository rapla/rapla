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

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.Builder.PreperationResult;
import org.rapla.components.calendarview.swing.scaling.IRowScale;
import org.rapla.components.calendarview.swing.scaling.LinearRowScale;
import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaLocale;

/** Graphical component for displaying a calendar like weekview.
 */
public class SwingWeekView extends AbstractSwingCalendar
{
    public final static int SLOT_GAP= 5;
    LargeDaySlot[] daySlots = new LargeDaySlot[] {};
    private int startMinutes= 0;
    private int endMinutes= 24 * 60;
    BoxLayout        boxLayout2= new BoxLayout(jCenter, BoxLayout.X_AXIS);
    TimeScale       timeScale = new TimeScale();
    IRowScale rowScale = new LinearRowScale();
    
    protected JLabel weekTitle;
    protected SelectionHandler selectionHandler ;
    
    public SwingWeekView() {
        this(true);
    }

    public SwingWeekView(boolean showScrollPane) {
        super(showScrollPane);
        weekTitle = new JLabel();
        weekTitle.setHorizontalAlignment( JLabel.CENTER);
        
        weekTitle.setFont(weekTitle.getFont().deriveFont((float)11.));

        jCenter.setLayout(boxLayout2);
        jCenter.setAlignmentY(JComponent.TOP_ALIGNMENT);
        jCenter.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        if ( showScrollPane ) {
            scrollPane.setRowHeaderView(timeScale);
            scrollPane.setCorner( JScrollPane.UPPER_LEFT_CORNER, weekTitle);
            
        } else {
            component.add(weekTitle,  "0,0");
            component.add(timeScale,"0,1");
        }
        selectionHandler = new SelectionHandler(this);
       
    }

   
    
    public void updateSize(int width) {
		int slotCount = 0;
		int columnCount = 0;
		for (int i=0; i<daySlots.length; i++) {
			LargeDaySlot largeDaySlot = daySlots[i];
            if ( isExcluded(i) )  {
                continue;
            }
            
            if ( largeDaySlot != null)
            {
            	slotCount += largeDaySlot.getSlotCount();
            	columnCount++;
            }
		}
		int newWidth = Math.round(((width - timeScale.getWidth() - 10- columnCount* (16 + SLOT_GAP)) / (Math.max(1,slotCount)))-2);
		newWidth = Math.max( newWidth , minBlockWidth);
		for (LargeDaySlot slot: daySlots)
		{
    		if ( slot != null)
    		{
    			slot.updateSize(newWidth);
    		}
    	}
		setSlotSize(newWidth);
	}

    public void setWorktime(int startHour, int endHour) 
    {
    	this.startMinutes = startHour * 60;
    	this.endMinutes = endHour * 60;
    }
    
    public void setWorktimeMinutes(int startMinutes, int endMinutes) {
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
        if (getStartDate() != null)
            calcMinMaxDates( getStartDate() );
    }


    public void setLocale(RaplaLocale locale) {
        super.setLocale( locale );
        if ( timeScale != null )
            timeScale.setLocale( locale );
    }


    public void scrollDateVisible(Date date) {
        LargeDaySlot slot = getSlot(date);
        if ( slot == null)
        {
        	return;
        }
		if (!jCenter.isAncestorOf( slot) ) {
            return;
        }
        LargeDaySlot scrollSlot = slot;
        Rectangle viewRect = scrollPane.getViewport().getViewRect();
        Rectangle slotRect = scrollSlot.getBounds();
        // test if already visible
        if (slotRect.x>=viewRect.x &&
            (slotRect.x + slotRect.width)<
            (viewRect.x + viewRect.width )
            )
        {
            return;
        }

        scrollSlot.scrollRectToVisible(new Rectangle(0
                                                     ,viewRect.y
                                                     ,scrollSlot.getWidth()
                                                     ,10));
    }

    public void scrollToStart() {
        int y = rowScale.getStartWorktimePixel();
        int x = 0;
        scrollPane.getViewport().setViewPosition(new Point(x,y));
    }

    public Collection<Block> getBlocks() {
        ArrayList<Block> list = new ArrayList<Block>();
        for (int i=0;i<getDaysInView();i++) {
            list.addAll(daySlots[i].getBlocks());
        }
        return Collections.unmodifiableCollection( list );
    }



    /** The granularity of the selection rows.
     * <ul>
     * <li>1:  1 rows per hour =   1 Hour</li>
     * <li>2:  2 rows per hour = 1/2 Hour</li>
     * <li>3:  3 rows per hour = 20 Minutes</li>
     * <li>4:  4 rows per hour = 15 Minutes</li>
     * <li>6:  6 rows per hour = 10 Minutes</li>
     * <li>12: 12 rows per hour =  5 Minutes</li>
     * </ul>
     * Default is 4.
     */
    public void setRowsPerHour(int rowsPerHour) {
        if ( rowScale instanceof LinearRowScale)
            ((LinearRowScale)rowScale).setRowsPerHour(rowsPerHour);
    }

    /** @see #setRowsPerHour */
    public int getRowsPerHour() {
        if ( rowScale instanceof LinearRowScale)
            return ((LinearRowScale)rowScale).getRowsPerHour();
        return 0;
    }

    /** The size of each row (in pixel). Default is 15.*/
    public void setRowSize(int rowSize) {
        if ( rowScale instanceof LinearRowScale)
            ((LinearRowScale)rowScale).setRowSize(rowSize);
    }

    public int getRowSize() {
        if ( rowScale instanceof LinearRowScale)
            return ((LinearRowScale)rowScale).getRowSize();
        return 0;
    }

    public void setBackground(Color color) {
        super.setBackground(color);
        if (timeScale != null)
            timeScale.setBackground(color);
    }

    public void setEditable(boolean b)  {
        super.setEditable( b );
        // Hide the rest
        for (int i= 0;i<daySlots.length;i++) {
            LargeDaySlot slot = daySlots[i];
            if (slot == null) continue;
            slot.setEditable(b);
            slot.getHeader().setBorder(b ? SLOTHEADER_BORDER : null);
        }
    }

    /** must be called after the slots are filled*/
    protected boolean isEmpty(int column)
    {
        return daySlots[column].isEmpty();
    }

    public void rebuild(Builder b) {
    	daySlots= new LargeDaySlot[getColumnCount()];
    	selectionHandler.clearSelection();
    	
        // clear everything
        jHeader.removeAll();
        jCenter.removeAll();

        
        int start = startMinutes ;
        int end = endMinutes ;

        // calculate the blocks
        PreperationResult prep = b.prepareBuild(getStartDate(),getEndDate());
        if (! bEditable) {
            start = Math.min(prep.getMinMinutes(),start);
            end = Math.max(prep.getMaxMinutes(),end);
            if (start<0)
                throw new IllegalStateException("builder.getMin() is smaller than 0");
            if (end>24*60)
                throw new IllegalStateException("builder.getMax() is greater than 24");
        }

        //rowScale = new VariableRowScale();
        if ( rowScale instanceof LinearRowScale)
        {
            LinearRowScale linearScale = (LinearRowScale) rowScale;
            int pixelPerHour = linearScale.getRowsPerHour() * linearScale.getRowSize();
            
            timeScale.setBackground(component.getBackground());
            if ( isEditable())
            {
                timeScale.setTimeIntervall(0, 24, pixelPerHour);
                linearScale.setTimeIntervall( 0, 24 * 60);
            }
            else
            {
                timeScale.setTimeIntervall(start / 60, Math.min( 24,(int)Math.ceil(end / 60.0)), pixelPerHour);
                final int endMinute = Math.min( 24 * 60, ((end / 60) + ((end%60 != 0) ? 1 : 0)) * 60 );
                linearScale.setTimeIntervall( (start /60) * 60, endMinute);
            }
            linearScale.setWorktimeMinutes( this.startMinutes, this.endMinutes);
        }
        else
        {
            timeScale.setBackground(component.getBackground());
            timeScale.setTimeIntervall(0, 24, 60);
        }
        
        // create Slots
        DraggingHandler draggingHandler = new DraggingHandler(this, rowScale,true);
        
        for (int i=0; i<getColumnCount(); i++) {
            createMultiSlot(i, i, draggingHandler, selectionHandler);
        }

        // build Blocks
        b.build(this, prep.getBlocks());
        
        // add Slots
        for (int i=0; i<daySlots.length; i++) {
            if ( isExcluded(i) )  {
                continue;
            }
            addToWeekView(daySlots[i]);
        }
        jHeader.add(Box.createGlue());
        jHeader.validate();
        jCenter.validate();
        if ( isEditable())
        {
        	updateSize(component.getSize().width);
        }
        component.revalidate();
        component.repaint();
    }

    private void createMultiSlot(int pos, int column, DraggingHandler draggingHandler, SelectionHandler selectionHandler)  {
        JComponent header = createSlotHeader(column);
        LargeDaySlot c= new LargeDaySlot(slotSize,rowScale, header);
        c.setEditable(isEditable());
        c.setTimeIntervall();
        c.setDraggingHandler(draggingHandler);
        c.addMouseListener(selectionHandler);
        c.addMouseMotionListener(selectionHandler);
        daySlots[pos]= c;
    }

   	protected Date getDateFromColumn(int column) {
		return DateTools.addDays( getStartDate(), column);
	}

    /** override this method, if you want to create your own slot header. */
    protected JComponent createSlotHeader(Integer column) {
        JLabel jLabel = new JLabel();
        jLabel.setBorder(isEditable() ? SLOTHEADER_BORDER : null);
        Date date = getDateFromColumn(column);
        jLabel.setText(raplaLocale.formatDayOfWeekDateMonth(date));
        jLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        jLabel.setHorizontalAlignment(JLabel.CENTER);
        jLabel.setOpaque(false);
        jLabel.setForeground(Color.black);
        return jLabel;
    }
    
	protected int getColumnCount() 
	{
		return getDaysInView();
	}

    public void addBlock(Block bl, int col,int slot) {
        checkBlock( bl );
        LargeDaySlot dslot =  daySlots[col];
        dslot.putBlock((SwingBlock)bl, slot);
    }

    private LargeDaySlot getSlot(Date start) {
    	 long countDays = DateTools.countDays(DateTools.cutDate(getStartDate()), start);
         int slotNr = (int) countDays;
         if ( daySlots.length ==0)
         {
        	 return null;
         }
         int min = Math.min(daySlots.length-1,slotNr);
		return daySlots[min];
	}

	private void addToWeekView(LargeDaySlot slot) {
        jHeader.add(slot.getHeader());
        jHeader.add(Box.createHorizontalStrut(SLOT_GAP));
        jCenter.add(slot);
        jCenter.add(Box.createHorizontalStrut(SLOT_GAP));
    }

    public int getSlotNr(DaySlot slot) {
        for (int i=0;i<daySlots.length;i++) {
            if (daySlots[i] == slot) {
                return i;
            }
        }
        throw new IllegalStateException("Slot not found in List");
    }

    int getRowsPerDay() {
        return rowScale.getRowsPerDay();
    }

    LargeDaySlot getSlot( int nr ) {
        if ( nr >=0 && nr< daySlots.length)
            return daySlots[nr];
        else
            return null;
    }
    
    public boolean isSelected(int slotNr)
    {
    	LargeDaySlot slot = getSlot(slotNr);
    	if ( slot == null)
    	{
    		return false;
    	}
    	return slot.isSelected();
    }

    int getDayCount() {
        return daySlots.length;
    }

    int calcSlotNr(int x, int y) {
        for (int i=0;i<daySlots.length;i++) {
            if (getSlot(i) == null)
                continue;
            Point p = getSlot(i).getLocation();
            if (p.x <= x
                    && x <= p.x + daySlots[i].getWidth()
                    && p.y <= y
                    && y <= p.y + daySlots[i].getHeight()
            )
                return i;
        }
        return -1;
    }

    public LargeDaySlot calcSlot(int x, int y) {
        int nr = calcSlotNr(x, y);
        if (nr == -1)
            return null;
        else
            return daySlots[nr];
    }
    

    protected Date createDate(DaySlot slot,int index, boolean startOfRow) {
        Date startDate = DateTools.cutDate(getStartDate());
        Date date = DateTools.getFirstWeekday(startDate,getFirstWeekday());
        date = DateTools.addDays(date , getSlotNr( slot ) %getDaysInView());
        if (!startOfRow)
            index++;
        int calcHour = rowScale.calcHour(index);
        int calcMinute = rowScale.calcMinute(index);
        date = new Date(date.getTime() +calcHour * DateTools.MILLISECONDS_PER_HOUR + calcMinute * DateTools.MILLISECONDS_PER_MINUTE );
        return date;
    }
    
  
}
