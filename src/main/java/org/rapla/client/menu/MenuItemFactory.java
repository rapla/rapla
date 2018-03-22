package org.rapla.client.menu;

import io.reactivex.functions.Consumer;
import org.rapla.client.PopupContext;
import org.rapla.components.i18n.I18nIcon;

public interface MenuItemFactory {
    MenuInterface createMenu(String text, I18nIcon icon);
    IdentifiableMenuEntry createMenuItem(String text, I18nIcon icon, Consumer<PopupContext> action);
}
