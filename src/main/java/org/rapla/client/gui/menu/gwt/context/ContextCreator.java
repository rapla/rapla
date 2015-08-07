package org.rapla.client.gui.menu.gwt.context;

import org.rapla.client.gui.menu.data.Point;
import org.rapla.client.gwt.GWTPopupContext;
import org.rapla.gui.PopupContext;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.user.client.Window;

public class ContextCreator
{
    public PopupContext createContext(DomEvent<?> event)
    {
        final NativeEvent nativeEvent = event.getNativeEvent();
        final int clientX = nativeEvent.getClientX();
        final int scrollLeft = Window.getScrollLeft();
        final int clientY = nativeEvent.getClientY();
        final int scrollTop = Window.getScrollTop();
        final int x = clientX + scrollLeft;
        final int y = clientY + scrollTop;
        final Point point = new Point(x, y);
        return new GWTPopupContext(point);
    }
}
