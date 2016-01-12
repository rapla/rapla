package org.rapla.client.menu;

import org.rapla.client.PopupContext;
import org.rapla.client.base.View;
import org.rapla.client.menu.MenuView.Presenter;
import org.rapla.client.menu.data.MenuCallback;
import org.rapla.client.menu.data.MenuEntry;
import org.rapla.framework.RaplaException;

import java.util.List;

public interface MenuView<W> extends View<Presenter>
{

    interface Presenter
    {
    }

    void showException(RaplaException ex);

    void showMenuPopup(List<MenuEntry> menu, PopupContext popupContext, MenuCallback menuCallback);

}
