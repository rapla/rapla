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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

import org.rapla.components.calendarview.AbstractCalendar;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.CalendarView;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.TimeInterval;


public abstract class AbstractSwingCalendar extends AbstractCalendar implements CalendarView {
  
    static Border SLOTHEADER_BORDER = new EtchedBorder();
    int slotSize = 100;
    boolean bEditable = true;
    protected int minBlockWidth = 3;

 
	ArrayList<ViewListener> listenerList = new ArrayList<ViewListener>();

    JScrollPane scrollPane = new JScrollPane();
    JPanel jHeader = new JPanel();
    BoxLayout boxLayout1 = new BoxLayout(jHeader, BoxLayout.X_AXIS);
    JPanel jCenter = new JPanel();
    protected JPanel jTitlePanel = new JPanel();
    protected JPanel component = new JPanel();

    AbstractSwingCalendar(boolean showScrollPane) {
        jHeader.setLayout(boxLayout1);
        jHeader.setOpaque( false );
        jCenter.setOpaque( false );
        jTitlePanel.setOpaque( false);
        jTitlePanel.setLayout( new BorderLayout());
        jTitlePanel.add(jHeader,BorderLayout.CENTER);
        if (showScrollPane) {
        	component.setLayout(new BorderLayout());
        	component.add(scrollPane,BorderLayout.CENTER);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setViewportView(jCenter);
          
            

            scrollPane.setColumnHeaderView(jTitlePanel);
            scrollPane.getVerticalScrollBar().setUnitIncrement(10);
            scrollPane.getHorizontalScrollBar().setUnitIncrement(10);
            scrollPane.setBorder(null);
        } else {
        	component.setLayout(new TableLayout(new double[][] {
                    {TableLayout.PREFERRED,TableLayout.FILL}
                    ,{TableLayout.PREFERRED,TableLayout.FILL}
            }));
        	component.add(jTitlePanel,"1,0");
        	component.add(jCenter,"1,1");
        }
        this.timeZone = TimeZone.getDefault();
        setLocale( Locale.getDefault() );
        
        if ( showScrollPane)
        {
	        component.addComponentListener( new ComponentListener() {
	        	private boolean resizing = false;
	        	 
				public void componentShown(ComponentEvent e) {
					
				}
				
				public void componentResized(ComponentEvent e) {
		
					if ( resizing)
					{
						return;
					}
					resizing = true;
					if ( isEditable() )
					{
						int width = component.getSize().width;
						updateSize(width);
					}
					SwingUtilities.invokeLater(new Runnable() {
						
						public void run() {
							resizing = false;
						}
					});
				
					
				}
	
				
				
				public void componentMoved(ComponentEvent e) {
				}
				
				public void componentHidden(ComponentEvent e) {
				}
			});
        }
    }

    abstract public void updateSize(int width);

	public JComponent getComponent()
    {
    	return component;
    }

    void checkBlock( Block bl ) {
        if ( !bl.getStart().before(this.getEndDate())) {
            throw new IllegalStateException("Start-date " +bl.getStart() + " must be before calendar end at " +this.getEndDate());
        }
    }

    public boolean isEditable() {
        return bEditable;
    }

    public void setEditable( boolean editable ) {
        bEditable = editable;
    }
    

    /**
       Width of a single slot in pixel.
    */
    public void setSlotSize(int slotSize) {
    	this.slotSize =  slotSize;
    }

    public int getSlotSize() {
        return slotSize;
    }
    
    /** the minimum width of a block in pixel */
    public int getMinBlockWidth()
    {
  		return minBlockWidth;
  	}

    /** the minimum width of a block in pixel */
  	public void setMinBlockWidth(int minBlockWidth) 
  	{
  		this.minBlockWidth = Math.max(3,minBlockWidth);
  	}


    public void setBackground(Color color) {
    	component.setBackground(color);
        if (scrollPane != null)
            scrollPane.setBackground(color);
        if (jCenter != null)
            jCenter.setBackground(color);
        if (jHeader != null)
            jHeader.setBackground(color);
    }

   

    public void addCalendarViewListener(ViewListener listener) {
        listenerList.add(listener);
    }

    public void removeCalendarViewListener(ViewListener listener) {
        listenerList.remove(listener);
    }

    JScrollPane getScrollPane() {
        return scrollPane;
    }

    void scrollTo(int x, int y) {
        JViewport viewport = scrollPane.getViewport();
        Rectangle rect = viewport.getViewRect();

        int leftBound = rect.x;
        int upperBound = rect.y;
        int lowerBound = rect.y + rect.height;
        int rightBound = rect.x + rect.width;
        int maxX = viewport.getView().getWidth();
        int maxY = viewport.getView().getHeight();


        JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
        if ( y > lowerBound && lowerBound < maxY) {
            scrollBar.setValue(scrollBar.getValue() + 20);
        }
        if ( y < upperBound && upperBound >0) {
            scrollBar.setValue(scrollBar.getValue() - 20);
        }

        scrollBar = scrollPane.getHorizontalScrollBar();
        if ( x > rightBound && rightBound < maxX) {
            scrollBar.setValue(scrollBar.getValue() + 20);
        }
        if ( x < leftBound && leftBound >0) {
            scrollBar.setValue(scrollBar.getValue() - 20);
        }
    }

  

    public ViewListener[] getWeekViewListeners() {
        return listenerList.toArray(new ViewListener[]{});
    }

    final void fireSelectionChanged(Date start, Date end) {
        // Fire the popup event
        ViewListener[] listeners = getWeekViewListeners();
        for (int i=0;i<listeners.length;i++) {
            listeners[i].selectionChanged(start,end);
        }
    }

    final void fireSelectionPopup(Component slot, Point p, Date start, Date end, int slotNr) {
        // Fire the popup event
        ViewListener[] listeners = getWeekViewListeners();
        for (int i=0;i<listeners.length;i++) {
            listeners[i].selectionPopup(slot,p,start,end, slotNr);
        }
    }

    final void fireMoved(SwingBlock block, Point p, Date newTime, int slotNr) {
        // Fire the popup event
        ViewListener[] listeners = getWeekViewListeners();
        for (int i=0;i<listeners.length;i++) {
            listeners[i].moved(block,p,newTime, slotNr);
        }
    }

    final void fireResized(SwingBlock block, Point p, Date newStart, Date newEnd, int slotNr) {
        // Fire the popup event
        ViewListener[] listeners = getWeekViewListeners();
        for (int i=0;i<listeners.length;i++) {
            listeners[i].resized(block,p,newStart,newEnd, slotNr);
        }
    }

    void fireResized(SwingBlock block, Point p, Date newTime, int slotNr) {
        // Fire the popup event
        ViewListener[] listeners = getWeekViewListeners();
        for (int i=0;i<listeners.length;i++) {
            listeners[i].moved(block,p,newTime, slotNr);
        }
    }

    void fireBlockPopup(SwingBlock block, Point p) {
        // Fire the popup event
        ViewListener[] listeners = getWeekViewListeners();
        for (int i=0;i<listeners.length;i++) {
            listeners[i].blockPopup(block,p);
        }
    }

    void fireBlockEdit(SwingBlock block, Point p) {
        // Fire the popup event
        ViewListener[] listeners = getWeekViewListeners();
        for (int i=0;i<listeners.length;i++) {
            listeners[i].blockEdit(block,p);
        }
    }

    abstract int getDayCount();
    abstract DaySlot getSlot(int num);
    abstract public boolean isSelected(int nr);
    abstract int calcSlotNr( int x, int y);
    abstract int getSlotNr( DaySlot slot);
    abstract int getRowsPerDay();
    abstract Date createDate( DaySlot slot, int row, boolean startOfRow);

	public TimeInterval normalizeBlockIntervall(SwingBlock block) {
		return new TimeInterval(block.getStart(), block.getEnd());
	}


}
