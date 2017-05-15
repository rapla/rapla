package org.rapla.client.extensionpoints;

import org.rapla.client.swing.toolkit.IdentifiableMenuEntry;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=HelpMenuExtension.ID)
public interface HelpMenuExtension extends IdentifiableMenuEntry
{
    String ID = "org.rapla.client.swing.gui.ExtraMenuInsert";
}
