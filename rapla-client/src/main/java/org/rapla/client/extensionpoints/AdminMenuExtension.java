package org.rapla.client.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=AdminMenuExtension.ID)
public interface AdminMenuExtension extends RaplaMenuExtension
{
    String ID = "AdminMenuInsert";
}
