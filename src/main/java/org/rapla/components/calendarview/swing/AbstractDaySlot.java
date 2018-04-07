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

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

abstract class AbstractDaySlot extends JPanel implements DaySlot
{
    private static final long serialVersionUID = 1L;
    private boolean bEditable = true;
    boolean paintDraggingGrid;
    int draggingSlot;
    int draggingY;
    SwingBlock draggingView;
    int draggingHeight;
    protected Map<Object,SwingBlock> blockViewMapper = new HashMap<>();
// BJO 00000076
//    protected Method getButtonMethod = null;
// BJO 00000076

    AbstractDaySlot() {
/* 
// BJO 00000076
        try {
            //is only available sind 1.4
            getButtonMethod = MouseEvent.class.getMethod("getButton", new Class[] {});
        } catch (Exception ex) {
        }
// BJO 00000076
*/ 
    }

    public void setEditable(boolean b) {
        bEditable = b;
    }

    public boolean isEditable() {
        return bEditable;
    }

    SwingBlock getBlockFor(Object component) {
        return blockViewMapper.get(component);
    }

    protected void showPopup(MouseEvent evt) {
        Point p = new Point(evt.getX(),evt.getY());
        SwingBlock block = getBlockFor(evt.getSource());
        if (block != null)
            draggingHandler.blockPopup(block,p);
    }

    protected Color getSelectionColor() {
        return UIManager.getColor("Table.selectionBackground");
    }

    public void paintDraggingGrid(int slot,int y, int height,SwingBlock draggingView,int oldY,int oldHeight,boolean bPaint) {
        this.paintDraggingGrid = bPaint;
        this.draggingSlot = slot;
        this.draggingY = y;
        this.draggingHeight = height;
        this.draggingView = draggingView;
        this.invalidateDragging();
    }

    public int getX(Component component) {
        return component.getParent().getLocation().x;
    }

    void invalidateDragging() {
        repaint();
    }

    void setDraggingHandler(DraggingHandler draggingHandler) {
        this.draggingHandler = draggingHandler;
    }

    private DraggingHandler draggingHandler;
    /** BlockListener handles the dragging events and the Block
        * Context Menu (right click).
        */
    class BlockListener extends MouseAdapter  {
           boolean preventDragging = false;

           public void mouseClicked(MouseEvent evt) {
               if (evt.isPopupTrigger()) {
                   showPopup(evt);
               } else {
                   if (evt.getClickCount()>1)
                       blockEdit(evt);
               }
               //draggingPointOffset = evt.getY();
           }

           private int calcResizeDirection( MouseEvent evt ) {
               if ( !draggingHandler.supportsResizing())
                   return 0;
               int height = ((Component)evt.getSource()).getHeight();
               int diff = height- evt.getY() ;
               if ( diff <= 5 && diff >=0 )
                   return 1;
               if (evt.getY() >=0 && evt.getY() < 5)
                   return -1;
               return 0;
           }

           public void mousePressed(MouseEvent evt) {
               if (evt.isPopupTrigger()) {
                   showPopup(evt);
               }
               
               preventDragging = false;
               /* 
// BJO 00000076
               //System.out.println ("Button:" +evt.getButton() );
               if ( getButtonMethod != null) {
                   try {
                       Integer button = (Integer) getButtonMethod.invoke( evt, new Object [] {});
                       preventDragging = button.intValue() != 1;
                   } catch (Exception ex) {
                   }
               }
               */
               preventDragging = evt.getButton() != 1;
// BJO 00000076
               if ( preventDragging )
                   return;
               SwingBlock block = getBlockFor(evt.getSource());
               int resizeDirection = calcResizeDirection( evt );
               if ( resizeDirection != 0) {
                   draggingHandler.blockBorderPressed( AbstractDaySlot.this,block, evt, resizeDirection );
               }
           }

           public void mouseReleased(MouseEvent evt) {
               if (evt.isPopupTrigger()) {
                   showPopup(evt);
               }
               preventDragging = false;
               SwingBlock block = getBlockFor(evt.getSource());
               draggingHandler.mouseReleased( AbstractDaySlot.this, block, evt);
           }

           public void mouseEntered(MouseEvent evt) {
           }

           public void mouseExited(MouseEvent evt) {
               if ( draggingHandler.isDragging())
                   return;
               setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
           }

           public void mouseMoved(MouseEvent evt) {
               if ( draggingHandler.isDragging()) {
                   return;
               }
               SwingBlock block = getBlockFor(evt.getSource());
               // BJO 00000137
               if(!block.isMovable())
            	   return;
               // BJO 00000137
               if ( calcResizeDirection( evt ) == 1 && block.isEndResizable()) {
                   setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
               } else if (calcResizeDirection( evt ) == -1 && block.isStartResizable()){
                   setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
               } else {
                   setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
               }
           }

           public void mouseDragged(MouseEvent evt) {
               if ( preventDragging  || !isEditable())
                   return;
              
               SwingBlock block = getBlockFor(evt.getSource());
                   
               draggingHandler.mouseDragged( AbstractDaySlot.this, block, evt);
           }


           private void blockEdit(MouseEvent evt) {
               SwingBlock block = getBlockFor(evt.getSource());
               draggingHandler.blockEdit(block,new Point(evt.getX(),evt.getY()));
           }
    }

    protected void paintDraggingGrid(Graphics g, int x, int y, int width, int height) {
    	/*
    	Rectangle rect = g.getClipBounds();
    	int startx = draggingView.getView().getX();
    	int starty = draggingView.getView().getY();
    	g.setColor(Color.gray);
        for (int y1=10;y1<height-5; y1+=11)
            for (int x1=10;x1<width-5; x1+=11) {
                int x2 = startx + x1;
                int y2 = starty + y1;
                if (
                        x2 >= rect.x
                        && x2 <= rect.x + rect.width
                        && y2 >= rect.y
                        && y2 <= rect.y + rect.height
                )
               	g.drawRect(x2,y2,2,2);
            }
            */
    	g.translate( x-1, y-1);

        if ( draggingView != null) {
        	 draggingView.paintDragging( g, width , height +1 );
        }
        g.translate( -(x-1), -(y-1));


    }

    static int count = 0;
}




