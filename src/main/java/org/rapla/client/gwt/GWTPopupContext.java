package org.rapla.client.gwt;

import org.rapla.client.gui.menu.data.Point;
import org.rapla.gui.PopupContext;

public class GWTPopupContext implements PopupContext
{
    private final Point point;

    public GWTPopupContext(Point point)
    {
        this.point = point;
    }

    public Point getPoint()
    {
        return point;
    }
}