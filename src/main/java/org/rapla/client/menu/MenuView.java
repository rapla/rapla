package org.rapla.client.menu;

import java.util.List;

import org.rapla.client.PopupContext;
import org.rapla.client.menu.data.MenuCallback;
import org.rapla.client.menu.data.MenuEntry;
import org.rapla.framework.RaplaException;

public interface MenuView<W> 
{

    interface Presenter
    {
    }
    
    public void setPresenter(Presenter presenter);

    void showException(RaplaException ex);

    void showMenuPopup(List<MenuEntry> menu, PopupContext popupContext, MenuCallback menuCallback);

}
