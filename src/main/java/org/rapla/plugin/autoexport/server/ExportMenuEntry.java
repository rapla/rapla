package org.rapla.plugin.autoexport.server;

import javax.inject.Inject;

import org.rapla.inject.Extension;
import org.rapla.plugin.autoexport.AutoExportResources;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

@Extension(provides = HtmlMainMenu.class,id="exportedcalendars")
public class ExportMenuEntry extends DefaultHTMLMenuEntry implements  HtmlMainMenu
{
	@Inject
	public ExportMenuEntry(AutoExportResources i18n) {
        
		super(i18n.getString( "calendar_list"),"rapla/calendar");

	}
	
}