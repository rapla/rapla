package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

/**
 * PRD 031 Phase 3: link to the Angular SPA on the chooser landing page (/, /index).
 * Title falls back to "Open web app" if i18n key is missing — minimal-friction wiring.
 */
public class RaplaSpaEntry extends DefaultHTMLMenuEntry implements HtmlMainMenu
{
    public RaplaSpaEntry(RaplaResources i18n)
    {
        // TODO add i18n key "open_web_app" to the RaplaResources bundle; hardcoded for now.
        super("Open web app", "app/");
    }
}
