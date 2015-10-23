package org.rapla.client.menu.gwt;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.PopupPanel;
import org.rapla.client.base.AbstractView;
import org.rapla.client.menu.MenuView;
import org.rapla.client.menu.data.MenuCallback;
import org.rapla.client.menu.data.MenuEntry;
import org.rapla.client.menu.data.Point;
import org.rapla.client.gwt.GwtPopupContext;
import org.rapla.client.gwt.components.MenuPopup;
import org.rapla.framework.RaplaException;
import org.rapla.client.PopupContext;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import java.util.List;

import javax.inject.Inject;

@DefaultImplementation(of =MenuView.class, context = InjectionContext.gwt)
public class MenuViewImpl extends AbstractView<MenuView.Presenter> implements MenuView<IsWidget>
{
    
    @Inject
    public MenuViewImpl()
    {
    }

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
        if (context != null && context instanceof GwtPopupContext)
        {
            p = ((GwtPopupContext) context).getPoint();
        }
        new MenuPopup(menu, menuCallback, p);
    }

}
