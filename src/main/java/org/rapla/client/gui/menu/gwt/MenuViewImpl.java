package org.rapla.client.gui.menu.gwt;

import java.util.List;

import org.rapla.client.base.AbstractView;
import org.rapla.client.gui.menu.MenuView;
import org.rapla.client.gui.menu.data.MenuCallback;
import org.rapla.client.gui.menu.data.MenuEntry;
import org.rapla.client.gui.menu.data.Point;
import org.rapla.client.gwt.GWTPopupContext;
import org.rapla.client.gwt.components.MenuPopup;
import org.rapla.framework.RaplaException;
import org.rapla.gui.PopupContext;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.PopupPanel;

public class MenuViewImpl extends AbstractView<org.rapla.client.gui.menu.MenuView.Presenter> implements MenuView<IsWidget>
{

    @Override
    public void showException(RaplaException ex)
    {
        final PopupPanel popupPanel = new PopupPanel(true, true);
        popupPanel.add(new HTML(ex.getMessage()));
        popupPanel.center();
        popupPanel.show();
    }

    @Override
    public void showMenuPopup(List<MenuEntry> menu, PopupContext context, MenuCallback menuCallback)
    {
        Point p = null;
        if (context != null && context instanceof GWTPopupContext)
        {
            p = ((GWTPopupContext) context).getPoint();
        }
        new MenuPopup(menu, menuCallback, p);
    }

}
