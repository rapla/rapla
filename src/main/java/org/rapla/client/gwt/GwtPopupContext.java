package org.rapla.client.gwt;

import org.rapla.client.gui.menu.data.Point;
import org.rapla.gui.PopupContext;

/**
 * Created by Christopher on 03.09.2015.
 */
public class GwtPopupContext implements PopupContext
{
    Point point;
    public GwtPopupContext(Point point)
    {
        this.point = point;
    }

    public Point getPoint()
    {
        return point;
    }
}
