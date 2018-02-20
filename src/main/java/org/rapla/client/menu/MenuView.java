package org.rapla.client.menu;

import org.rapla.client.PopupContext;
import org.rapla.client.menu.data.MenuCallback;
import org.rapla.client.menu.data.MenuEntry;
import org.rapla.framework.RaplaException;

import java.util.List;

public interface MenuView<W> 
{

    interface Presenter
    {
    }
    
    public void setPresenter(Presenter presenter);

    Void showException(Throwable ex);

    void showMenuPopup(List<MenuEntry> menu, PopupContext popupContext, MenuCallback menuCallback);

}
