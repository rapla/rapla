package org.rapla.client.gwt;

import org.rapla.client.PopupContext;
import org.rapla.client.menu.sandbox.data.Point;

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
    
    public static Point extractPoint(PopupContext context)
    {
        if(context instanceof GwtPopupContext)
        {
            return ((GwtPopupContext)context).getPoint();
        }
        return null;
    }
}
