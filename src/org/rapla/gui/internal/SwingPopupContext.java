package org.rapla.gui.internal;

import java.awt.Component;
import java.awt.Point;

import org.rapla.gui.PopupContext;

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
    
    
}
