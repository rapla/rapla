package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.inject.Singleton;


@Singleton
public class RaplaStatusEntry extends DefaultHTMLMenuEntry implements HtmlMainMenu
{
    public final static String ID = "3_status";
    @Autowired
    public RaplaStatusEntry(RaplaResources i18n)
    {
        super(i18n.getString("server_status"), "server");
    }

}
