package org.rapla.client.menu.sandbox.data;

import java.util.ArrayList;
import java.util.List;

public class MenuEntry
{
    private final List<MenuEntry> subEntries = new ArrayList<>();
    private final String text;
    private final String icon;
    private boolean enabled = true;

    public MenuEntry(String text, String icon, boolean enabled)
    {
        this.text = text;
        this.icon = icon;
        this.enabled = enabled;
    }

    public List<MenuEntry> getSubEntries()
    {
        return subEntries;
    }

    public String getText()
    {
        return text;
    }

    public String getIcon()
    {
        return icon;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

}
