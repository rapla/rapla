package org.rapla.client.extensionpoints;

import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=HelpMenuExtension.ID)
public interface HelpMenuExtension extends RaplaMenuExtension
{
    String ID = "org.rapla.client.swing.gui.ExtraMenuInsert";
}
