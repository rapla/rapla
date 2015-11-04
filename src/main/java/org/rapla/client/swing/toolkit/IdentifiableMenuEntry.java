package org.rapla.client.swing.toolkit;

import javax.swing.MenuElement;

/** Adds an id to the standard Swing Menu ServerComponent as JSeperator, JMenuItem and JMenu*/
public interface IdentifiableMenuEntry 
{
    String getId();
    MenuElement getMenuElement();
}
