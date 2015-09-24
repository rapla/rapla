package org.rapla.client.extensionpoints;

import org.rapla.entities.RaplaObject;
import org.rapla.gui.MenuContext;
import org.rapla.gui.toolkit.RaplaMenuItem;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/** add your own menu entries in the context menu of an object. To do this provide
 an ObjectMenuFactory under this entry.
 */
@ExtensionPoint(context = InjectionContext.swing,id="contextmenu")
public interface ObjectMenuFactory
{
    RaplaMenuItem[] create(MenuContext menuContext,RaplaObject focusedObject);
}
