package org.rapla.client.extensionpoints;

import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.entities.RaplaObject;

/** add your own menu entries in the context menu of an object. To do this provide
 an ObjectMenuFactory under this entry.
 */

public interface ObjectMenuFactory
{
    String ID = "contextmenu";
    IdentifiableMenuEntry[] create(SelectionMenuContext menuContext, RaplaObject focusedObject);
}
