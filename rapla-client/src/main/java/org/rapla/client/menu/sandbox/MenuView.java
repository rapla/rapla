package org.rapla.client.menu.sandbox;

import org.rapla.client.PopupContext;
import org.rapla.client.menu.sandbox.data.MenuCallback;
import org.rapla.client.menu.sandbox.data.MenuEntry;

import java.util.List;

public interface MenuView<W> 
{

    interface Presenter
    {
    }
    
    void setPresenter(Presenter presenter);

    Void showException(Throwable ex);

    void showMenuPopup(List<MenuEntry> menu, PopupContext popupContext, MenuCallback menuCallback);

}
