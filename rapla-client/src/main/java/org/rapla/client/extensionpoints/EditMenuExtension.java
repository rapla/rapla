package org.rapla.client.extensionpoints;

import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=EditMenuExtension.ID)
public interface EditMenuExtension extends RaplaMenuExtension
{
    String ID = "org.rapla.client.swing.gui.EditMenuInsert";
}
