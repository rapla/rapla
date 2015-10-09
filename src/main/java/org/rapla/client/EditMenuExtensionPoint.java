package org.rapla.client;

import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(id="org.rapla.gui.EditMenuInsert",context = InjectionContext.swing)
public interface EditMenuExtensionPoint extends IdentifiableMenuEntry
{
}
