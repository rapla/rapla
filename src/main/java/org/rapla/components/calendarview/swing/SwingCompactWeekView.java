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

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.calendarview.Builder.PreperationResult;
import org.rapla.components.calendarview.swing.SelectionHandler.SelectionStrategy;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Graphical component for displaying a calendar like weekview.
 * This view doesn't show the times and arranges the different slots
 * Vertically.
*/
public class SwingCompactWeekView extends AbstractSwingCalendar
{
    private SmallDaySlot[] slots = new SmallDaySlot[0];
    private List<List<Block>> rows = new ArrayList<>();
    DraggingHandler draggingHandler = new DraggingHandler(this, false);
    SelectionHandler selectionHandler = new SelectionHandler(this);
    String[] rowNames = new String[] {};
    int leftColumnSize = 100;
    Map<Block, Integer> columnMap = new HashMap<>();

    public SwingCompactWeekView() {
        this(true);
    }

    public SwingCompactWeekView(boolean showScrollPane) {
        super( showScrollPane );
        selectionHandler.setSelectionStrategy(SelectionStrategy.BLOCK);
    }

    public Collection<Block> getBlocks() {
        ArrayList<Block> list = new ArrayList<>();
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

    public void setSlots(String[] slotNames) {
        this.rowNames = slotNames;
    }

    TableLayout tableLayout;
    public void rebuild(Builder b) {
        rows.clear();
        columnMap.clear();
        for ( int i=0; i<rowNames.length; i++ ) {
            addRow();
        }
        

        
        // clear everything
        jHeader.removeAll();
        jCenter.removeAll();

        // calculate the blocks
        PreperationResult prep = b.prepareBuild(getStartDate(), getEndDate());
        b.build(this,getStartDate(), prep.getBlocks());
        
        tableLayout= new TableLayout();
        jCenter.setLayout(tableLayout);

//        calendar.setTime(getStartDate());
//        int firstDayOfWeek = getFirstWeekday();
//        calendar.set( Calendar.DAY_OF_WEEK, firstDayOfWeek);

        if ( rowNames.length > 0) {
        	tableLayout.insertColumn(0, leftColumnSize);
        	jHeader.add( createColumnHeader( null ), "0,0,l,t" );
        } else {
        	tableLayout.insertColumn(0, 0);
        }

        int columns = getColumnCount();

        // add headers
        for (int column=0;column<columns;column++) {
            if ( !isExcluded(column) ) {
                tableLayout.insertColumn(column + 1, slotSize );
                jHeader.add( createColumnHeader( column ) );
            } else {
                tableLayout.insertColumn(column + 1, 0.);
            }
        }

        int rowsize = rows.size();
        slots = new SmallDaySlot[rowsize * columns];
        for (int row=0;row<rowsize;row++) {
            tableLayout.insertRow(row, TableLayout.PREFERRED );
            jCenter.add(createRowLabel( row ), "" + 0 + "," + row + ",f,t");
            for (int column=0;column < columns; column++) {
                int fieldNumber = row * columns + column;
                SmallDaySlot field = createField(   );
                for (Block block:getBlocksForColumn(rows.get( row ), column))
                {
                	field.putBlock( (SwingBlock ) block);
                }
				slots[fieldNumber] = field;
                if ( !isExcluded( column ) ) {
                    jCenter.add(slots[fieldNumber], "" + (column + 1) + "," + row);
                }
            }
        }
    	selectionHandler.clearSelection();
        jCenter.validate();
        jHeader.validate();
        if ( isEditable())
        {
        	updateSize(component.getSize().width);
        }
        component.revalidate();
        component.repaint();
    }

	protected int getColumnCount() 
	{
		return getDaysInView();
	}

    public int getLeftColumnSize() 
    {
		return leftColumnSize;
	}

	public void setLeftColumnSize(int leftColumnSize) 
	{
		this.leftColumnSize = leftColumnSize;
	}

	protected JComponent createRowLabel(int row)  {
        JLabel label = new JLabel();
        if ( row < rowNames.length ) {
            label.setText( rowNames[ row ] );
            label.setToolTipText(rowNames[ row ]);
        }
        return label;
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

    /** override this method, if you want to createInfoDialog your own slot header. */
    protected JComponent createColumnHeader(Integer column) {
        JLabel jLabel = new JLabel();
        jLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        jLabel.setHorizontalAlignment(JLabel.CENTER);
        jLabel.setOpaque(false);
        jLabel.setForeground(Color.black);
        Dimension dim;
        if (column != null ) {
        	Date date = getDateFromColumn(column);
            jLabel.setText(getRaplaLocale().formatDayOfWeekDateMonth(date));
            jLabel.setBorder(isEditable() ? SLOTHEADER_BORDER : null);
         	dim = new Dimension(this.slotSize,20);
        }
        else
        {
        	dim = new Dimension(this.leftColumnSize,20);
        	jLabel.setFont(jLabel.getFont().deriveFont((float)11.));
        	jLabel.setHorizontalAlignment(SwingConstants.LEFT);
        }
        jLabel.setPreferredSize( dim);
        jLabel.setMinimumSize( dim );
        jLabel.setMaximumSize( dim );
        return jLabel;
    }

    private SmallDaySlot createField( )  {
        SmallDaySlot c= new SmallDaySlot("",slotSize, Color.BLACK, null);
        c.setEditable(isEditable());
        c.setDraggingHandler(draggingHandler);
        c.addMouseListener(selectionHandler);
        c.addMouseMotionListener(selectionHandler);
        return c;
    }
    
    protected List<Block> getBlocksForColumn(List<Block> blocks,int column)
    {
    	List<Block> result = new ArrayList<>();
    	//Date startDate = getDateFromColumn(column);
    	if ( blocks != null) {
    		
    		Iterator<Block> it = blocks.iterator();
    		while (it.hasNext()){
    			Block block = it.next();
    			if (columnMap.get( block) == column) {
//    			if ( DateTools.cutDate( block.getStart()).equals( startDate)) {
    				result.add( block);
                }
           }
    	}
        return result;
    }

	protected Date getDateFromColumn(int column) {
		Date startDate = getStartDate();
		return DateTools.addDays( startDate, column);
	}

    public void addBlock(Block block, int column,int slot) {
        if ( rows.size() <= slot)
        {
            return;
        }
        checkBlock( block );
        List<Block> blocks =  rows.get( slot );
        blocks.add( block );
        columnMap.put(block, column);
    }

    private void addRow() {
        rows.add( rows.size(), new ArrayList<>());
    }

    public int getSlotNr( DaySlot slot) {
        for (int i=0;i<slots.length;i++)
            if (slots[i] == slot)
                return i;
        throw new IllegalStateException("Slot not found in List");
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
        Date startDate = DateTools.cutDate(getStartDate());
        Date date = DateTools.getFirstWeekday( startDate, getFirstWeekday());
		int column = getSlotNr( slot ) % getDaysInView();
        Date result = DateTools.addDays( date , column);
        //calendar.set( Calendar.DAY_OF_WEEK, getDayOfWeek(slot) );
        if ( !startOfRow ) {
            result =DateTools.addDays(result,1 );
        }
        return result;
    }
    

    /** must be called after the slots are filled*/
    protected boolean isEmpty(int column)
    { 
    	for (Integer value: columnMap.values())
    	{
    		if ( value.equals( column))
    		{
    			return false;
    		}
    	}
    	return true;
    }

	@Override
	public void updateSize(int width) {
		if ( tableLayout == null)
		{
			return;
		}
		
		int columnSize = tableLayout.getNumColumn();
		int realColumns= columnSize;
		for ( int i=1;i< columnSize;i++)
		{
			if( isExcluded(i))
			{
				realColumns--;
			}
		}
		int newWidth =  Math.max( minBlockWidth ,(width - leftColumnSize -20 - realColumns) /  (Math.max(1,realColumns-1)));
		for (SmallDaySlot slot: this.slots)
    	{
    		if ( slot != null)
    		{
    			slot.updateSize(newWidth);
    		}
    	}
		setSlotSize(newWidth);
		for ( int i=1;i< columnSize;i++)
		{
			if( !isExcluded(i-1))
			{
				tableLayout.setColumn(i, newWidth);
			}
		}
		boolean first = true;
		for (Component comp:jHeader.getComponents())
		{
			if ( first)
			{
				first = false;
				continue;
			}
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


