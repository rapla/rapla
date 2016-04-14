package org.rapla.server.internal;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.inject.Extension;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

@Extension(provides = HtmlMainMenu.class,id="status")
@Singleton
public class RaplaStatusEntry extends DefaultHTMLMenuEntry implements HtmlMainMenu
{
    @Inject
    public RaplaStatusEntry(RaplaResources i18n)
    {
        super(i18n.getString("server_status"), "rapla/server");
    }
}
