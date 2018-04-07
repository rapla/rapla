package org.rapla.components.i18n.client.swing;

import org.rapla.components.i18n.I18nIcon;

import javax.swing.ImageIcon;

public class SwingIcon implements I18nIcon
{
    private final String key;
    private final ImageIcon iconFromKey;
    public SwingIcon(String key, ImageIcon iconFromKey) {

        this.key = key;
        this.iconFromKey = iconFromKey;
    }

    public String getId() {
        return key;
    }

    public ImageIcon getIcon() {
        return iconFromKey;
    }
}
