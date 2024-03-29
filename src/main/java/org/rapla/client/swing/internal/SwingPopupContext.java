package org.rapla.client.swing.internal;

import org.rapla.client.PopupContext;

import java.awt.Component;
import java.awt.Point;

public class SwingPopupContext implements PopupContext
{
    Point point;
    Component parent;
    public SwingPopupContext(Component parent, Point p)
    {
        this.parent = parent;
        this.point = p;
    }
    
    public Component getParent()
    {
        return parent;
    }
    
    public Point getPoint()
    {
        return point;
    }
    
    public static Component extractParent(PopupContext popupContext){
        if(popupContext != null && popupContext instanceof SwingPopupContext){
            return ((SwingPopupContext) popupContext).getParent();
        }
        return null;
    }
    
    public static Point extractPoint(PopupContext popupContext){
        if(popupContext != null && popupContext instanceof SwingPopupContext){
            return ((SwingPopupContext) popupContext).getPoint();
        }
        return null;
    }
    
}
