package org.rapla.client.swing.internal;

import java.awt.Component;
import java.awt.Point;

import org.rapla.client.PopupContext;

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
            return SwingPopupContext.class.cast(popupContext).getParent();
        }
        return null;
    }
    
    public static Point extractPoint(PopupContext popupContext){
        if(popupContext != null && popupContext instanceof SwingPopupContext){
            return SwingPopupContext.class.cast(popupContext).getPoint();
        }
        return null;
    }
    
}
