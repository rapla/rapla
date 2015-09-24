package org.rapla.server.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.server.servletpages.RaplaPageGenerator;

/**
 * You can add arbitrary serlvet pages to your rapla webapp.
 *<p>
 * Example that adds a page with the name "my-page-name" and the class
 * "org.rapla.plugin.myplugin.MyPageGenerator". You can call this page with <code>rapla?page=my-page-name</code>
 * </p>
*/
@ExtensionPoint(context = InjectionContext.server,id="webpage")
public interface RaplaPageExtension extends RaplaPageGenerator
{
}
