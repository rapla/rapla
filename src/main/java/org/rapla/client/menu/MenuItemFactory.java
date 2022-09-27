package org.rapla.client.menu;

import io.reactivex.rxjava3.functions.Consumer;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.components.i18n.I18nIcon;

public interface MenuItemFactory {
    MenuInterface createMenu(String text, I18nIcon icon, String id);
    IdentifiableMenuEntry createMenuItem(String text, I18nIcon icon, Consumer<PopupContext> action);
    RaplaWidget createSeparator(String seperator);
}
