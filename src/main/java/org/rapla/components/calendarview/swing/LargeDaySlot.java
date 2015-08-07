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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.swing.scaling.IRowScale;


/** Komponente, welche eine beliebige anzahl von Slot-komponenten zusammenfasst.
* die slots koennen zur laufzeit hinzugefuegt oder auch dynamisch bei bedarf
*erzeugt werden. sie werden horizontal nebeneinander angeordnet.
*/
class LargeDaySlot extends AbstractDaySlot 
{
    private static final long serialVersionUID = 1L;

    public static Color THICK_LINE_COLOR = Color.black;
    public static Color LINE_COLOR = new Color(0xaa, 0xaa, 0xaa);
    public static Color WORKTIME_BACKGROUND = Color.white;
    public static Color NON_WORKTIME_BACKGROUND = new Color(0xcc, 0xcc, 0xcc);

    private List<Slot> slots= new ArrayList<Slot>();
    private int slotxsize;
    
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private int oldSelectionStart = -1;
    private int oldSelectionEnd = -1;

    BoxLayout boxLayout1 = new BoxLayout(this, BoxLayout.X_AXIS);
    int right_gap = 8;
    int left_gap = 5;
    int slot_space = 3;
    JComponent header;
    IRowScale rowScale;
    
    private BlockListener blockListener = new BlockListener();
    /**
       es muss auch noch setTimeIntervall() aufgerufen werden, um die initialisierung
       fertig zu stellen (wie beim konstruktor von Slot).
       slotxsize ist die breite der einzelnen slots.
       "date" legt das Datum fest, fuer welches das Slot anzeigt (Uhrzeit bleibt unberuecksichtigt)
    */
    public LargeDaySlot(
                     int slotxsize
                     ,IRowScale rowScale
                     ,JComponent header) {
        this.slotxsize= slotxsize;
        this.rowScale = rowScale;
        this.header = header;
        setLayout(boxLayout1);
        this.add(Box.createHorizontalStrut(left_gap));
        addSlot();
        setBackground(getBackground());
        setAlignmentX(0);
        setAlignmentY(TOP_ALIGNMENT);
        this.setBackground(Color.white);
    }

    public boolean isBorderVisible() {
        return true;
//      return getBackground() != Color.white;
    }

    public int getSlotCount()
    {
    	return slots.size();
    }
    
    public boolean isSelected()
    {
    	return selectionStart >=0 ;
    }
    
    public void setVisible(boolean b) {
        super.setVisible(b);
        header.setVisible(b);
    }

    public JComponent getHeader() {
        return header;
    }

    public int calcSlot(int x) {
        int slot = ((x - left_gap) / (slotxsize + slot_space));
        //System.out.println ( x + "  slot " + slot);
        if (slot<0)
            slot = 0;
        if (slot >= slots.size())
            slot = slots.size() -1;
        return slot;
    }

    public Collection<Block> getBlocks() {
        ArrayList<Block> list = new ArrayList<Block>();
        for (int i=0;i<slots.size();i++)
            list.addAll(slots.get(i).getBlocks());
        return list;
    }

    public void select(int start,int end) {
        if (start == selectionStart && end == selectionEnd)
            return;
        selectionStart = start;
        selectionEnd = end;
        invalidateSelection();
    }

    public void unselectAll() {
        if (selectionStart == -1 || selectionEnd == -1)
            return;
        selectionStart = -1;
        selectionEnd = -1;
        invalidateSelection();
    }

    public void setTimeIntervall() {
        Iterator<Slot> it= slots.iterator();
        while (it.hasNext()) {
            Slot slot = it.next();
            slot.setTimeIntervall();
        }
    }

    private int addSlot() {
        Slot slot= new Slot();
        slot.setTimeIntervall();
        slots.add(slot);
        this.add(slot);
        this.add(Box.createHorizontalStrut(slot_space));

        updateHeaderSize();

        return slots.size()-1;
    }

	public void updateHeaderSize() {
		Dimension size = header.getPreferredSize();
        Dimension newSize = new Dimension(slotxsize  * slots.size() //Slotwidth and border
                                          + slot_space * slots.size()  //Space between Slots
                                          + left_gap + right_gap  // left and right gap
                                          , (int)size.getHeight());
        header.setPreferredSize(newSize);
        header.setMaximumSize(newSize);
        header.invalidate();
	}

    /**
       fuegt einen block im gewuenschten slot ein
       (konflikte werden ignoriert).
    */
    public void putBlock(SwingBlock bl, int slotnr)  {
        while (slotnr >= slots.size()) {
            addSlot();
        }
        slots.get(slotnr).putBlock(bl);
        // The blockListener can be shared among all blocks,
        // as long es we can only click on one block simultanously
        bl.getView().addMouseListener(blockListener);
        bl.getView().addMouseMotionListener(blockListener);
        blockViewMapper.put(bl.getView(),bl);
    }

    public Dimension getMinimumSize() { return getPreferredSize(); }
    public Dimension getMaximumSize() { return getPreferredSize(); }
    Insets insets = new Insets(0,0,0, right_gap);
    Insets slotInsets = new Insets(0,0,0,0);

    public Insets getInsets() {
        return insets;
    }

    SwingBlock getBlockFor(Object component) {
        return blockViewMapper.get(component);
    }

    int getBlockCount() {
        int count = 0;
        Iterator<Slot> it = slots.iterator();
        while (it.hasNext())
            count += it.next().getBlockCount();
        return count;
    }

    boolean isEmpty() {
        return getBlockCount() == 0;
    }

    protected void invalidateDragging(Point oldPoint) {
        int width = getSize().width;
        int startRow = Math.min(calcRow(draggingY),calcRow(oldPoint.y ));
        int endRow = Math.max(calcRow(draggingY),calcRow(oldPoint.y )) + 1;
        repaint(0
                , rowScale.getYCoordForRow(startRow) -10
                , width
                , rowScale.getSizeInPixelBetween(startRow, endRow) + draggingHeight + 20
                );
    }

    private void invalidateSelection() {
        int width = getSize().width;
        int startRow = Math.min(selectionStart,oldSelectionStart);
        int endRow = Math.max(selectionEnd,oldSelectionEnd) + 1;
        repaint(0,rowScale.getYCoordForRow(startRow ), width, rowScale.getSizeInPixelBetween(startRow, endRow  ));
        // Update the values after calling repaint, because paint needs the old values.
        oldSelectionStart = selectionStart;
        oldSelectionEnd = selectionEnd;
    }

    public void paint(Graphics g)  {
        Dimension dim = getSize();
        Rectangle rect = g.getClipBounds();
        if (!isEditable()) {
            g.setColor(Color.white);
            g.fillRect(rect.x
                       ,rect.y
                       ,rect.width
                       ,rect.height);
        } else {
            int starty = rowScale.getStartWorktimePixel();
            int endy = rowScale.getEndWorktimePixel();
            int height = rowScale.getSizeInPixel();
            Color firstColor = NON_WORKTIME_BACKGROUND;
            Color secondColor = WORKTIME_BACKGROUND;
            
            if ( starty >= endy)
            {
                int c = starty;
                starty = endy;
                endy = c;
                secondColor = NON_WORKTIME_BACKGROUND;
                firstColor = WORKTIME_BACKGROUND;
            }
            
            if (rect.y - rect.height <= starty) {
                g.setColor( firstColor );
                g.fillRect(rect.x
                           ,Math.max(rect.y,0)
                           ,rect.width
                           ,Math.min(rect.height,starty));
            }
            if (rect.y + rect.height >= starty && rect.y <= endy ) {
                g.setColor( secondColor );
                g.fillRect(rect.x
                           ,Math.max(rect.y,starty)
                           ,rect.width
                           ,Math.min(rect.height,endy - starty));
            }
            if (rect.y + rect.height >= endy) {
                g.setColor( firstColor );
                g.fillRect(rect.x
                           ,Math.max(rect.y,endy)
                           ,rect.width
                           ,Math.min(rect.height, height - endy));
            }
        }

        if (isBorderVisible()) {
            g.setColor(LINE_COLOR);
            g.drawLine(0,rect.y,0,rect.y + rect.height);
            g.drawLine(dim.width - 1,rect.y,dim.width - 1 ,rect.y + rect.height);
        }

        int max = rowScale.getMaxRows() ;
        // Paint selection
        for (int i=0; i < max ; i++) {
            int rowSize = rowScale.getRowSizeForRow( i);
            int y = rowScale.getYCoordForRow( i);
            if ((y + rowSize) >= rect.y && y < (rect.y + rect.height)) {
                if (i>= selectionStart && i<=selectionEnd) {
                    g.setColor(getSelectionColor());
                    g.fillRect(Math.max (rect.x,1)
                               , y
                               , Math.min (rect.width,dim.width - Math.max (rect.x,1) - 1)
                               , rowSize);
                }
                boolean bPaintRowThick = (rowScale.isPaintRowThick(i));
                g.setColor((bPaintRowThick) ? THICK_LINE_COLOR : LINE_COLOR);
                if (isEditable() || (bPaintRowThick && (i<max || isBorderVisible())))
                    g.drawLine(rect.x,y ,rect.x + rect.width , y);
                if ( i == max -1 )
                {
                	g.setColor( THICK_LINE_COLOR );
                	g.drawLine(rect.x,y+rowSize ,rect.x + rect.width , y+rowSize);
                }
            }
        }

        super.paintChildren(g);
        if ( paintDraggingGrid ) {

        	int x = draggingSlot * (slotxsize + slot_space) + left_gap;
            paintDraggingGrid(g, x , draggingY, slotxsize -1,  draggingHeight);
        }
    }



    /** grafische komponente, in die implementierungen von Block (genauer deren zugehoerige BlockViews) eingefuegt werden koennen.
     * entsprechend start- und end-zeit des blocks wird der view
     * im slot dargestellt. die views werden dabei vertikal angeordnet.
     */
    class Slot extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private Collection<Block> blocks= new ArrayList<Block>();
       
        public Slot()  {
            setLayout(null);
            setBackground(Color.white);
            setOpaque(false);
        }

        /** legt fest, fuer welches zeitintervall das slot gelten soll.
         (Beispiel 8:00 - 13:00)
         min und max sind uhrzeiten in vollen stunden.
         zur initialisierung der komponente muss diese methode mindestens einmal
         aufgerufen werden.
         */
        public void setTimeIntervall()  {
            int ysize= rowScale.getSizeInPixel() + 1;
            setSize(slotxsize, ysize);
            Dimension preferredSize = new Dimension(slotxsize, ysize );
			setPreferredSize(preferredSize );
			setMinimumSize(preferredSize );
        }

        
        /** fuegt b in den Slot ein.  */
        public void putBlock(SwingBlock b)  {
            blocks.add(b);
            add(b.getView());
            updateBounds(b);
        }

		public void updateBounds(SwingBlock b) {
			//update bounds
            int y1= rowScale.getYCoord(b.getStart());
            final int minimumSize = 15;
            int y2= Math.max( y1+minimumSize,rowScale.getYCoord(b.getEnd()));
            if ( y1 <  0)
                y1 = 0;
            if ( y2 > getMaximumSize().height)
                y2 = getMaximumSize().height;
            b.getView().setBounds(0, y1, slotxsize, y2 - y1 + 1);
		}

        public int getBlockCount() {
            return blocks.size();
        }

        public Dimension getMinimumSize() { return getPreferredSize(); }
        public Dimension getMaximumSize() { return getPreferredSize(); }

        public Insets getInsets() {
            return slotInsets;
        }

        public Collection<Block> getBlocks() {
            return blocks;
        }
    }


    void updateSize(int slotsize)
    {
    	this.slotxsize = slotsize;
    	for ( Slot slot:slots)
    	{
    		slot.setTimeIntervall();
    		for (Block block:slot.getBlocks())
        	{
        		slot.updateBounds((SwingBlock)block);
        	}
    	}
    	updateHeaderSize();
    }
    
    public int calcRow( int y )
    {
        return rowScale.calcRow( y);
    }
}



