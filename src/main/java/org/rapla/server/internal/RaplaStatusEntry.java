package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.inject.Extension;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

import javax.inject.Inject;
import javax.inject.Singleton;

@Extension(provides = HtmlMainMenu.class,id=RaplaStatusEntry.ID)
@Singleton
public class RaplaStatusEntry extends DefaultHTMLMenuEntry implements HtmlMainMenu
{
    public final static String ID = "3_status";
    @Inject
    public RaplaStatusEntry(RaplaResources i18n)
    {
        super(i18n.getString("server_status"), "rapla/server");
    }

}
