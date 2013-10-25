package org.rapla.gui;

import org.rapla.entities.RaplaObject;
import org.rapla.gui.toolkit.RaplaMenuItem;

public interface ObjectMenuFactory
{
    RaplaMenuItem[] create(MenuContext menuContext,RaplaObject focusedObject);
}
