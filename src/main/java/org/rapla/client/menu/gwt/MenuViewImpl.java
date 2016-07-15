package org.rapla.client.menu.gwt;

import java.util.List;

import javax.inject.Inject;

import org.rapla.client.PopupContext;
import org.rapla.client.gwt.GwtPopupContext;
import org.rapla.client.gwt.components.MenuPopup;
import org.rapla.client.menu.MenuView;
import org.rapla.client.menu.data.MenuCallback;
import org.rapla.client.menu.data.MenuEntry;
import org.rapla.client.menu.data.Point;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.PopupPanel;

@DefaultImplementation(of =MenuView.class, context = InjectionContext.gwt)
public class MenuViewImpl  implements MenuView<IsWidget>
{
    
    private Presenter presenter;

    @Inject
    public MenuViewImpl()
    {
    }
    
    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }
    
    protected Presenter getPresenter() {
        return presenter;
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
