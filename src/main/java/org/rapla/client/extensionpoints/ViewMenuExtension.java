package org.rapla.client.extensionpoints;

import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=ViewMenuExtension.ID)
public interface ViewMenuExtension extends RaplaMenuExtension
{
    String ID = "ViewMenuInsert";
}
