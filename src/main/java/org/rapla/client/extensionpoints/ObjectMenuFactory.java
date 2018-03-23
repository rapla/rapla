package org.rapla.client.extensionpoints;

import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.entities.RaplaObject;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/** add your own menu entries in the context menu of an object. To do this provide
 an ObjectMenuFactory under this entry.
 */
@ExtensionPoint(context = InjectionContext.client,id=ObjectMenuFactory.ID)
public interface ObjectMenuFactory
{
    String ID = "contextmenu";
    IdentifiableMenuEntry[] create(SelectionMenuContext menuContext, RaplaObject focusedObject);
}
