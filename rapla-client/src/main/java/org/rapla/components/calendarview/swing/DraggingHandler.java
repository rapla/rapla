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
package org.rapla.components.calendarview.swing;

import org.rapla.components.calendarview.swing.scaling.IRowScaleSmall;
import org.rapla.components.calendarview.swing.scaling.OneRowScale;
import org.rapla.components.util.TimeInterval;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Date;


/** DraggingHandler coordinates the drag events from the Block-Components
 * between the different MultiSlots of a weekview.
 */
class DraggingHandler {
    int draggingPointOffset = 0;
    DaySlot oldSlot;
    int oldY = 0;
    int oldHeight = 0;
    AbstractSwingCalendar m_cv;
    Date start = null;
    Date newStart = null;
    Date end = null;
    Date newEnd = null;
    int resizeDirection;
    boolean bMoving;
    boolean bResizing;
    boolean supportsResizing;
    IRowScaleSmall rowScale;

    public DraggingHandler(AbstractSwingCalendar wv,IRowScaleSmall rowScale, boolean supportsResizing) {
        this.supportsResizing = supportsResizing;  
        this.rowScale = rowScale;
        m_cv = wv;
    }
    
    public DraggingHandler(AbstractSwingCalendar wv, boolean supportsResizing) {
        this ( wv, new OneRowScale(), supportsResizing );
    }
    
    public boolean supportsResizing() {
        return supportsResizing;    
    }

    public void blockPopup(SwingBlock block,Point p) {
        m_cv.fireBlockPopup(block,p);
    }

    public void blockEdit(SwingBlock block,Point p) {
        m_cv.fireBlockEdit(block,p);
    }

    public void mouseReleased(DaySlot slot, SwingBlock block, MouseEvent evt) {
        if ( isDragging() )
           stopDragging(slot, block, evt);
    }
    
    public void blockBorderPressed(DaySlot slot,SwingBlock block,MouseEvent evt, int direction) {
        if (!bResizing && supportsResizing ) {
            this.resizeDirection = direction;
            startResize( slot, block, evt);
        }
    }

    public boolean isDragging() {
        return bResizing || bMoving;
    }
    
    public void mouseDragged(DaySlot slot,SwingBlock block,MouseEvent evt) {
        if ( bResizing )
             startResize( slot, block, evt );
        else 
             startMoving( slot, block, evt );
    }
    
    private void dragging(DaySlot slot,SwingBlock block,int _x,int _y,boolean bDragging) {
        // 1. Calculate slot
        DaySlot newSlot = null;
        if ( bResizing ) {
            newSlot = slot; 
        } else {
            int slotNr = m_cv.calcSlotNr(
                slot.getLocation().x + _x
                , slot.getLocation().y + _y);
            newSlot = m_cv.getSlot( slotNr );
            if (newSlot == null)
                return;
        }

        // 2. Calculate new x relative to slot
        
        int y = _y;
        int xslot = 0;
        int height = block.getView().getHeight();
        xslot = newSlot.calcSlot( slot.getLocation().x + _x - newSlot.getLocation().x );
        if ( bResizing ) {
            if ( resizeDirection == 1) {
                y = block.getView().getLocation().y;
                //  we must trim the endRow
                int endrow = newSlot.calcRow(_y ) + 1;
                endrow = Math.max( newSlot.calcRow(y) + 2, endrow);
                height = rowScale.getYCoordForRow(endrow) - y;
                if ( bDragging ) { 
                    start = block.getStart();
                    end =  m_cv.createDate( newSlot, endrow, true);
                    //System.out.println ( "Resizeing@end: start=" + start + ", end=" + end) ;
                }
            } else if (resizeDirection == -1){
                //  we must trim y
                y = rowScale.trim( y );
                int row = newSlot.calcRow( y ) ;
                int rowSize = rowScale.getRowSizeForRow( row );
                y = Math.min ( block.getView().getLocation().y + block.getView().getHeight() - rowSize, y );
                height = block.getView().getLocation().y  + block.getView().getHeight() - y;
                if ( bDragging ) { 
                	row = newSlot.calcRow( y );
                	if(y==0)
                		start = m_cv.createDate( newSlot, row, true);
                	else
                		start = m_cv.createDate( newSlot, row, false);

                    end = block.getEnd();
                    //System.out.println ( "Resizeing@start: start=" + start + ", end=" + end) ;
                }
            }
        } else if (bMoving){ 
            // we must trim y
            //y = rowScale.trim( y);
            if ( bDragging ) {
            	int newRow = newSlot.calcRow( y );
            	start = m_cv.createDate( newSlot, newRow, true);
            	y =  rowScale.trim( y );
                //System.out.println ( "Moving: start=" + start + ", end=" + end +" row: " + row) ;
            }
        }
        if (oldSlot != null && oldSlot != newSlot)
            oldSlot.paintDraggingGrid(xslot, y, height, block, oldY, oldHeight, false);

        newSlot.paintDraggingGrid(xslot, y, height, block, oldY, oldHeight, bDragging);
        oldSlot = newSlot;
        oldY = y;
        oldHeight = height;
    }
    
    private void startMoving(DaySlot slot,SwingBlock block,MouseEvent evt) {   
        if (!bMoving) {
            draggingPointOffset = evt.getY();
            if (block.isMovable()) {
                bMoving = true;
                slot.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            } else {
                bMoving = false;
                slot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }
        }
        if ( block == null)
            return;
        int x = evt.getX() + slot.getX(block.getView());
        int y = evt.getY() + block.getView().getLocation().y;

        scrollTo( slot, x, y);
        // Correct y with the draggingPointOffset
        y = evt.getY() - draggingPointOffset + block.getView().getLocation().y ;
        
        y += rowScale.getDraggingCorrection(y ) ;
        dragging( slot, block, x, y, bMoving);
    }
    
    private void startResize(DaySlot slot,SwingBlock block, MouseEvent evt) {
        if ( block == null)
            return;
        int x = evt.getX() + slot.getX(block.getView());
        int y = evt.getY() + block.getView().getLocation().y;
        if (!bResizing) {
            if (block.isMovable() && (   ( resizeDirection == -1 && block.isStartResizable() ) 
                                                 || ( resizeDirection == 1 && block.isEndResizable()))) {
                bResizing = true;
            } else {
                bResizing = false;
                return;
            }
        }
        
        scrollTo( slot, x, y);
        dragging( slot, block, x, y, bResizing);
    }
    
    private void stopDragging(DaySlot slot, SwingBlock block,MouseEvent evt) {
        slot.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        if ( block == null) {
        	return ;
        }
        
        if (!m_cv.isEditable()) 
            return;
        try {
            int x = evt.getX() + slot.getX( block.getView() );
            int y = evt.getY() - draggingPointOffset + block.getView().getLocation().y ;
            y += rowScale.getDraggingCorrection(y );
            
            dragging(slot,block,x,y,false);
            Point upperLeft = m_cv.getScrollPane().getViewport().getViewPosition();
            Point newPoint = new Point(slot.getLocation().x + x -upperLeft.x
                    ,y-upperLeft.y);
            int slotNr = m_cv.getSlotNr(oldSlot); 
            TimeInterval normalizedInterval = m_cv.normalizeBlockIntervall(block);
            Date blockStart = normalizedInterval.getStart();
            Date blockEnd = normalizedInterval.getEnd();
			if ( bMoving ) {
                // Has the block moved
                //System.out.println("Moved to " + newStart + " - " + newEnd);
                if ( !start.equals( blockStart ) || oldSlot!= slot) {
                    m_cv.fireMoved(block, newPoint, start, slotNr);
                }
            }
            if ( bResizing ) {
                // System.out.println("Resized to " + start + " - " + end);
                if ( !( start.equals( blockStart )  && end.equals( blockEnd) )) {
                    m_cv.fireResized(block, newPoint, start, end, slotNr);
                }
            }
        } finally {
            bResizing = false;
            bMoving = false;
            start = null; 
            end = null; 
        }
    }    
    
    // Begin scrolling when hitting the upper or lower border while
    // dragging or selecting.
    private void scrollTo(DaySlot slot,int x,int y) {
        // 1. Transfer p.x relative to jCenter
        m_cv.scrollTo(slot.getLocation().x + x, slot.getLocation().y + y);
    }

}

