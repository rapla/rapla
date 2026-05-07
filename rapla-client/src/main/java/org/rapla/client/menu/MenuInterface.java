package org.rapla.client.menu;

import org.rapla.client.RaplaWidget;

import javax.swing.event.MenuListener;
import java.util.function.Consumer;

/** JPopupMenu and JMenu don't have a common interface, so this is a common interface
 * for RaplaMenu and RaplaPopupMenu
*/
public interface MenuInterface extends IdentifiableMenuEntry {
    void addMenuItem(IdentifiableMenuEntry newItem);
    void addSeparator();
    void removeAll();

    void removeAllBetween(String startId, String endId);
    void insertAfterId(RaplaWidget component, String id);
    void insertBeforeId(RaplaWidget component,String id);
    void setEnabled(boolean enabled);
    void addSelectionListener(Consumer<Boolean> selected);
    void setInitializer(Runnable initializer);
    void setTitle(String title);

}