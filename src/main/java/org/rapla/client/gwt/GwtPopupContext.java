package org.rapla.client.gwt;

import org.rapla.client.PopupContext;
import org.rapla.client.menu.data.Point;

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
