package org.rapla.server.servletpages;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.server,id="webpage")
public interface RaplaPageExtenstion extends RaplaPageGenerator
{
}
