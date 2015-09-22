package org.rapla.server.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.server.servletpages.RaplaPageGenerator;

@ExtensionPoint(context = InjectionContext.server,id="webpage")
public interface RaplaPageExtension extends RaplaPageGenerator
{
}
