package org.rapla.client.gwt.components;

import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.PopupPanel;
import org.rapla.client.menu.sandbox.data.MenuCallback;
import org.rapla.client.menu.sandbox.data.MenuEntry;
import org.rapla.client.menu.sandbox.data.Point;

import java.util.List;

public class MenuPopup
{
    private final PopupPanel menu;

    public MenuPopup(final List<MenuEntry> items, final MenuCallback menuCallback, final Point point)
    {
        menu = new PopupPanel(true, true);
        final MenuBar menuBar = new MenuBar(true);
        menuBar.setAutoOpen(true);
        for (MenuEntry item : items)
        {
            createStructure(menuBar, item, menuCallback, menu);
        }
        menu.add(menuBar);
        if (point == null)
        {
            menu.center();
        }
        else
        {
            menu.setPopupPosition(point.getX(), point.getY());
        }
        menu.show();
    }
    
    private void createStructure(final MenuBar menuBar, final MenuEntry item, final MenuCallback menuCallback, final PopupPanel menu)
    {
        if (item.getSubEntries().isEmpty())
        {
            menuBar.addItem(item.getText(), true, () -> {
                menu.hide();
                menu.clear();
                menu.removeFromParent();
                menuCallback.selectEntry(item);
            });
        }
        else
        {
            final List<MenuEntry> subEntries = item.getSubEntries();
            final MenuBar subMenuBar = new MenuBar(true);
            final MenuItem newEntry = menuBar.addItem(item.getText(), true, subMenuBar);
            newEntry.setEnabled(item.isEnabled());
            for (MenuEntry menuEntry : subEntries)
            {
                createStructure(subMenuBar, menuEntry, menuCallback, menu);
            }
        }
    }

}
