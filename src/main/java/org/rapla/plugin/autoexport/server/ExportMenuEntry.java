package org.rapla.plugin.autoexport.server;

import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.autoexport.AutoExportResources;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.DefaultHTMLMenuEntry;

import javax.inject.Inject;

@Extension(provides = HtmlMainMenu.class,id="exportedcalendars")
public class ExportMenuEntry extends DefaultHTMLMenuEntry implements  HtmlMainMenu
{
	RaplaFacade facade;

	@Inject
	public ExportMenuEntry(AutoExportResources i18n,RaplaFacade facade) {
		super(i18n.getString( "calendar_list"),"rapla/calendar");
		this.facade = facade;
	}

	@Override
	public boolean isEnabled()
	{
		try
		{
			final Preferences systemPreferences = facade.getSystemPreferences();
			final Boolean entryAsBoolean = systemPreferences.getEntryAsBoolean(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, false);
			return entryAsBoolean;
		}
		catch (RaplaException ex)
		{
			return true;
		}
	}
}