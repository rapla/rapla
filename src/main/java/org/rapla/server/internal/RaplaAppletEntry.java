package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.inject.Extension;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

import javax.inject.Inject;
import javax.inject.Singleton;

//@Extension(provides = HtmlMainMenu.class,id="2_applet")
@Singleton
public class RaplaAppletEntry extends DefaultHTMLMenuEntry implements HtmlMainMenu
{
    @Inject
    public RaplaAppletEntry(RaplaResources i18n)
    {
        super( i18n.getString("start_rapla_with_applet"), "rapla/raplaapplet");
    }
}
