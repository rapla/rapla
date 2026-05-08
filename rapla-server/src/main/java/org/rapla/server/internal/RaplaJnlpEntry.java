package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.inject.Singleton;


@Singleton
public class RaplaJnlpEntry extends DefaultHTMLMenuEntry implements HtmlMainMenu
{
    @Autowired
    public RaplaJnlpEntry(RaplaResources i18n)
    {
        super( i18n.getString("start_rapla_with_webstart"), "raplaclient.jnlp");
    }
}
