package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.framework.RaplaContext;
import org.rapla.inject.Extension;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

import javax.inject.Inject;

@Extension(provides = HtmlMainMenu.class,id="jnlp")
public class RaplaJnlpEntry extends DefaultHTMLMenuEntry implements HtmlMainMenu
{
    @Inject
    public RaplaJnlpEntry(RaplaResources i18n)
    {
        super( i18n.getString("start_rapla_with_webstart"), "rapla/raplaclient.jnlp");
    }
}
