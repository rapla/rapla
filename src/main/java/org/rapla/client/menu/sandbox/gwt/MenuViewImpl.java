package org.rapla.client.menu.sandbox.gwt;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.PopupPanel;
import org.rapla.client.PopupContext;
import org.rapla.client.gwt.GwtPopupContext;
import org.rapla.client.gwt.components.MenuPopup;
import org.rapla.client.menu.sandbox.MenuView;
import org.rapla.client.menu.sandbox.data.MenuCallback;
import org.rapla.client.menu.sandbox.data.MenuEntry;
import org.rapla.client.menu.sandbox.data.Point;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import java.util.List;

@DefaultImplementation(of =MenuView.class, context = InjectionContext.gwt)
public class MenuViewImpl implements MenuView<IsWidget>
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
    public Void showException(Throwable ex)
    {
        final PopupPanel popupPanel = new PopupPanel(true, true);
        popupPanel.add(new HTML(ex.getMessage()));
        popupPanel.center();
        popupPanel.show();
        return Promise.VOID;
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
