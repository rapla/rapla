package org.rapla.server.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.server.servletpages.RaplaMenuGenerator;

/** you can add your own entries on the index page Just add HtmlMainMenu extension
 @see RaplaMenuGenerator
  * */
@ExtensionPoint(context = InjectionContext.server,id="mainmenu")
public interface HtmlMainMenu extends RaplaMenuGenerator
{
}
