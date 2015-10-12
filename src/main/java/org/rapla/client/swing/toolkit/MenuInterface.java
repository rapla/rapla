package org.rapla.client.swing.toolkit;

import java.awt.Component;

import javax.swing.JComponent;

import org.rapla.client.swing.RaplaAction;

/** JPopupMenu and JMenu don't have a common interface, so this is a common interface
 * for RaplaMenu and RaplaPopupMenu
*/
public interface MenuInterface {
    void add(RaplaAction item);
    void remove(RaplaAction item);
    void addSeparator();
    void removeAll();

    void removeAllBetween(String startId, String endId);
    void insertAfterId(Component component,String id);
    void insertBeforeId(JComponent component,String id);

}