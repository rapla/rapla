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

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Date;

import javax.swing.SwingUtilities;

/** SelectionHandler handles the  selection events and the Slot
 * Context Menu (right click).
 * This is internally used by the weekview to communicate with its slots.
 */
public class SelectionHandler extends MouseAdapter {
    Date start;
    Date end;
    boolean bPopupClicked = false;
    boolean bSelecting = false;
    public int selectionStart = -1;
    public int selectionEnd = -1;
    private int oldIndex = -1;
    private int oldSlotNr = -1;
    private int startSlot = -1;
    private int endSlot = -1;
    private int draggingSlot = -1;
    private int draggingIndex = -1;
    AbstractSwingCalendar m_wv;
    public enum SelectionStrategy
    {
    	FLOW,BLOCK
    }
    SelectionStrategy selectionStrategy = SelectionStrategy.FLOW;
    
    public SelectionHandler(AbstractSwingCalendar wv) {
        m_wv = wv;
    }

    public void mouseClicked(MouseEvent evt) {
        if (evt.isPopupTrigger()) {
            bPopupClicked = true;
            slotPopup(evt);
        } else {
            /* We don't check click here
            if (SwingUtilities.isLeftMouseButton(evt))
                move(evt);
                */
        }
    }
    public void mousePressed(MouseEvent evt) {
        if (evt.isPopupTrigger()) {
            bPopupClicked = true;
            move(evt, true);
                slotPopup(evt);
        } else {
            if (SwingUtilities.isLeftMouseButton(evt))
                move(evt,false);
        }
    }

    public void mouseReleased(MouseEvent evt) {
        if (evt.isPopupTrigger()) {
            bPopupClicked = true;
            move(evt, true);
            slotPopup(evt);
        }
        if (SwingUtilities.isLeftMouseButton(evt) && !bPopupClicked)
            move(evt, false);
        bPopupClicked = false;
        bSelecting = false;
    }

    public void mouseDragged(MouseEvent evt) {
        if (SwingUtilities.isLeftMouseButton(evt) && !bPopupClicked)
            move(evt, false);
    }
    public void mouseMoved(MouseEvent evt) {
    }

    public void setSelectionStrategy(SelectionStrategy strategy) {
           selectionStrategy = strategy;
    }
    
    public void clearSelection() {
        for (int i=0;i<m_wv.getDayCount();i++)
        {
            if (m_wv.getSlot(i) != null)
            {
                m_wv.getSlot(i).unselectAll();
            }
        }
        selectionStart = -1;
        selectionEnd = -1;
        oldIndex = -1;
        oldSlotNr = -1;
        startSlot = -1;
        endSlot = -1;
        draggingSlot = -1;
        draggingIndex = -1;
    }
    

    public void slotPopup(MouseEvent evt) {
        Point p = new Point(evt.getX(),evt.getY());
        DaySlot slot= (DaySlot)evt.getSource();
        if (start == null || end == null) {
            int index = slot.calcRow( evt.getY() );
            start = m_wv.createDate(slot, index, true);
            end = m_wv.createDate(slot, index, false);
            clearSelection();
            slot.select(index,index);
        }
        m_wv.fireSelectionPopup((Component)slot,p,start,end,m_wv.getSlotNr( slot ));
    }

    public void move(MouseEvent evt, boolean contextPopup) {
        if (!m_wv.isEditable()) 
            return;
        DaySlot source = (DaySlot) evt.getSource();
        int slotNr;
        {
        	Point location = source.getLocation();
			slotNr = m_wv.calcSlotNr(
                location.x + evt.getX()
                ,location.y + evt.getY()
                );
        }
        if (slotNr == -1)
            return;

        int selectedIndex = source.calcRow(evt.getY());
        if ( contextPopup && inCurrentSelection(slotNr,selectedIndex))
        {
        	return;
        }
        if (!bSelecting) {
            clearSelection();
            bSelecting = true;
            selectionStart = selectedIndex;
            selectionEnd = selectedIndex;
            draggingSlot =slotNr;
            draggingIndex = selectedIndex;
            startSlot = slotNr;
            endSlot = slotNr;
        } else {
            if (slotNr == draggingSlot) {
                startSlot = endSlot = slotNr;
                if ( selectedIndex > draggingIndex ) {
                    selectionStart = draggingIndex;
                    selectionEnd = selectedIndex;
                } else if (selectedIndex < draggingIndex ){
                    selectionStart = selectedIndex;
                    selectionEnd = draggingIndex;
                }
            } else if (slotNr > draggingSlot) {
                startSlot = draggingSlot;
                selectionStart = draggingIndex;
                endSlot = slotNr;
                selectionEnd = selectedIndex;
            } else if (slotNr < draggingSlot) {
                startSlot = slotNr;
                selectionStart = selectedIndex;
                endSlot = draggingSlot;
                selectionEnd = draggingIndex;
            }
            if (selectedIndex == oldIndex && slotNr == oldSlotNr)
            {
                return;
            }
            int rowsPerDay = m_wv.getRowsPerDay();
            if (selectedIndex >= rowsPerDay-1)
            {
                selectedIndex = rowsPerDay-1;
            }
        }
        oldSlotNr = slotNr;
        oldIndex = selectedIndex;
        switch ( selectionStrategy)
        {
        	case BLOCK:  setSelectionBlock();break;
        	case FLOW: setSelectionFlow();break;
        }
        {
	        Point location = m_wv.getSlot(slotNr).getLocation();
			m_wv.scrollTo(
	                location.x + evt.getX()
	                ,location.y + evt.getY()
	        );
        }
    }

    private boolean inCurrentSelection(int slotNr, int selectedIndex) {
   	 	 if ( slotNr < startSlot || slotNr > endSlot)
    	 {
    		 return false;
    	 }
    	 if (slotNr == startSlot && selectedIndex < selectionStart) {
    		 return false;
    	 }
        return !(slotNr == endSlot && selectedIndex > selectionEnd);
    }

	protected void setSelectionFlow() {

        int startRow = selectionStart;
        int endRow = m_wv.getRowsPerDay() -1;
        
        
        for (int i=0;i<startSlot;i++) 
        {
            DaySlot daySlot = m_wv.getSlot(i);
			if (daySlot != null) {
                daySlot.unselectAll();
            }
        }
     
       int dayCount = m_wv.getDayCount();
	   for (int i=startSlot;i<=endSlot;i++) 
	   {
           if (i > startSlot)
           {
               startRow = 0;
           }
           if (i == endSlot)
           {
        	   endRow = selectionEnd;
           }
           DaySlot slot = m_wv.getSlot(i);
           if (slot != null)
           {
        	   slot.select(startRow,endRow);
           }
       }
       startRow = selectionStart ;
       endRow = selectionEnd ;
       for (int i=endSlot+1;i<dayCount;i++) {
           DaySlot slot = m_wv.getSlot(i);
           if (slot != null)
           {
        	   slot.unselectAll();
           }
      }
       

      start = m_wv.createDate(m_wv.getSlot(startSlot),startRow, true);
      end = m_wv.createDate(m_wv.getSlot(endSlot),endRow, false);
      m_wv.fireSelectionChanged(start,end);
    }
    
    protected void setSelectionBlock() {
    	int startRow = selectionStart;
    	int endRow = selectionEnd;
    	Point endSlotLocation = m_wv.getSlot(endSlot).getLocation();
  	   	Point startSlotLocation = m_wv.getSlot(startSlot).getLocation();

  	   	int min_y = Math.min(startSlotLocation.y, endSlotLocation.y);
  	   	int min_x = Math.min(startSlotLocation.x, endSlotLocation.x);

  	   	int max_y = Math.max(startSlotLocation.y, endSlotLocation.y);
  	   	int max_x = Math.max(startSlotLocation.x, endSlotLocation.x);

        int dayCount = m_wv.getDayCount();

        for (int i=0;i<dayCount;i++) {
            DaySlot slot = m_wv.getSlot(i);

            if (slot != null) {
            	 Point location = slot.getLocation();
            	 if ( location.x >= min_x &&  location.x <= max_x &&  location.y >= min_y &&  location.y <= max_y)
            	 {
            		 slot.select(startRow,endRow);
            	 }
            	 else
            	 {
            		 slot.unselectAll();
            	 }
            }
        }
        start = m_wv.createDate(m_wv.getSlot(startSlot),startRow, true);
        end = m_wv.createDate(m_wv.getSlot(endSlot),endRow, false);
        m_wv.fireSelectionChanged(start,end);
    }
}



