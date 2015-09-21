package org.rapla.plugin.autoexport.server;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.framework.RaplaContext;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.autoexport.AutoExportResources;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

public class ExportMenuEntry extends DefaultHTMLMenuEntry
{
	private final AutoExportResources i18n;
	public ExportMenuEntry(RaplaContext context, AutoExportResources i18n) {
        
		super(context);
		this.i18n = i18n;
	}
	
	@Override
	public String getName() {
		return i18n.getString( "calendar_list");
	}
	@Override
	public String getLinkName() {
		return "rapla?page=calendarlist";
	}
	
}