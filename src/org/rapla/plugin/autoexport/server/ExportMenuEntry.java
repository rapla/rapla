package org.rapla.plugin.autoexport.server;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.framework.RaplaContext;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

public class ExportMenuEntry extends DefaultHTMLMenuEntry
{
	public ExportMenuEntry(RaplaContext context) {
        
		super(context);
	}
	
	@Override
	public String getName() {
		I18nBundle i18n = getService( AutoExportPlugin.AUTOEXPORT_PLUGIN_RESOURCE );
		return i18n.getString( "calendar_list");
	}
	@Override
	public String getLinkName() {
		return "rapla?page=calendarlist";
	}
	
}