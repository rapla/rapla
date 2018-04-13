package org.rapla.client.menu;

import io.reactivex.functions.Consumer;
import jsinterop.annotations.JsType;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.components.i18n.I18nIcon;

@JsType
public interface MenuItemFactory {
    MenuInterface createMenu(String text, I18nIcon icon, String id);
    IdentifiableMenuEntry createMenuItem(String text, I18nIcon icon, Consumer<PopupContext> action);
    RaplaWidget createSeparator(String seperator);
}
