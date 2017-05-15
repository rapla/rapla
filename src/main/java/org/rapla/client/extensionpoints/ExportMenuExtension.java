package org.rapla.client.extensionpoints;

import org.rapla.client.swing.toolkit.IdentifiableMenuEntry;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=ExportMenuExtension.ID)
public interface ExportMenuExtension extends IdentifiableMenuEntry
{
    String ID = "ExportMenuInsert";
}
