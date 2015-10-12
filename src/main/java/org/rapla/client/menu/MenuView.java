package org.rapla.client.menu;

import java.util.List;

import org.rapla.client.base.View;
import org.rapla.client.menu.MenuView.Presenter;
import org.rapla.client.menu.data.MenuCallback;
import org.rapla.client.menu.data.MenuEntry;
import org.rapla.framework.RaplaException;
import org.rapla.client.PopupContext;

public interface MenuView<W> extends View<Presenter>
{

    public interface Presenter
    {
    }

    void showException(RaplaException ex);

    void showMenuPopup(List<MenuEntry> menu, PopupContext popupContext, MenuCallback menuCallback);

}
