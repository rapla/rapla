/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.swing.internal;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.SystemOptionPanel;
import org.rapla.client.internal.CountryChooser;
import org.rapla.client.internal.LanguageChooser;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.rest.SettingsService;
import org.rapla.rest.dto.SystemSettings;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.RemoteLocaleService;
import org.rapla.storage.dbrm.RestartServer;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

@Service
@Scope("prototype")

public class RaplaStartOption extends RaplaGUIComponent implements SystemOptionPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaplaStartOption.class);
    JPanel panel = new JPanel();
    JTextField calendarName;
    Preferences preferences;
	private final JComboBox cboTimezone;
    private final JComboBox htmlCharset;
    private final JComboBox csvCharset;

    private final LanguageChooser languageChooser;
    private final CountryChooser countryChooser;
	ICalTimezones timezoneService;
	RaplaNumber seconds = new RaplaNumber(Double.valueOf(10),Double.valueOf(10),null, false);
    boolean isRestartPossible;
    private final SettingsService settings;

    @Override
    public RaplaResources getI18n()
    {
        return super.getI18n();
    }


    @Autowired
    public RaplaStartOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, ICalTimezones timezoneService, RemoteLocaleService localeService, IOInterface ioInterface, RestartServer restartServer, CommandScheduler scheduler, SettingsService settings) throws
            RaplaInitializationException {
        super(facade, i18n, raplaLocale);
        this.settings = settings;
        isRestartPossible = restartServer.isRestartPossible();
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout(new double[][] {{pre, 5,pre, 5, pre}, {pre,5,pre, 5 , pre, 5, pre,5 , pre, 5, pre ,5 , pre, 5, pre}}));
        this.timezoneService = timezoneService;      
        calendarName = new JTextField();
        addCopyPaste(calendarName, i18n, raplaLocale, ioInterface);
        calendarName.setColumns(20);
        panel.add(new JLabel(getString("custom_applicationame")), "0,0");
        panel.add(calendarName, "2,0");
        calendarName.setEnabled(true);

        String[] timeZoneIDs = new String[0];
        try
        {
            timeZoneIDs = getTimeZonesFromResource();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        panel.add(new JLabel(getString("timezone")), "0,2");
		@SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(timeZoneIDs);
		cboTimezone = jComboBox;
        String[] charsets = {AbstractRaplaLocale.ISO_8859_1_CHARSET, AbstractRaplaLocale.UTF_CHARSET};
        htmlCharset = new JComboBox(charsets);
        csvCharset = new JComboBox(charsets);

        panel.add(cboTimezone, "2,2");
		cboTimezone.setEditable(false);

        languageChooser = new LanguageChooser(i18n,raplaLocale);
        panel.add( new JLabel(i18n.getString("server.language") ), "0,4");
        panel.add( languageChooser.getComponent(), "2,4");

        countryChooser = new CountryChooser(i18n,raplaLocale,localeService,scheduler);
        panel.add( new JLabel(i18n.getString("server.country") ), "0,6");
        panel.add( countryChooser.getComponent(), "2,6");
        languageChooser.addActionListener(e -> countryChooser.changeLanguage(languageChooser.getSelectedLanguage()));

		
		seconds.getNumberField().setBlockStepSize( 60);
	    seconds.getNumberField().setStepSize( 10);
	    
        panel.add( new JLabel(getString("seconds")),"4,8"  );
        panel.add( seconds,"2,8");
        panel.add( new JLabel(getString("connection") + ": " + getI18n().format("interval.format", "","")),"0,8"  );
        panel.add( new JLabel("HTML Export Charset"),"0,10"  );
        panel.add(htmlCharset, "2,10");
        panel.add( new JLabel("CSV Export Charset"),"0,12"  );
        panel.add(csvCharset, "2,12");
        addCopyPaste( seconds.getNumberField(), i18n, raplaLocale, ioInterface);
    }

    public JComponent getComponent() {
        return panel;
    }
    public String getName(Locale locale) {
        return getString("options");
    }

    public void setPreferences( Preferences preferences) {
        this.preferences = preferences;
    }

    public void show() throws RaplaException {
        // Read fresh values via GET /settings/system instead of the bulk
        // /storage/resources preference cache. Save path is unchanged:
        // commit() writes to the editable prefs clone and the dialog
        // framework saves via facade.store -> /storage/dispatch.
        SystemSettings sys;
        try {
            LOGGER.info("RaplaStartOption.show(): fetching /settings/system via REST");
            sys = settings.getSystem();
        }
        catch (Exception e)
        {
            LOGGER.warn("GET /settings/system failed, falling back to local cache: {}", e.getMessage());
            try {
                String tzFallback = preferences.getEntryAsString(AbstractRaplaLocale.TIMEZONE, timezoneService.getDefaultTimezone());
                sys = new SystemSettings(
                        preferences.getEntryAsString(AbstractRaplaLocale.TITLE, ""),
                        tzFallback,
                        preferences.getEntryAsString(AbstractRaplaLocale.LOCALE, null),
                        preferences.getEntryAsString(AbstractRaplaLocale.CSV_CHARSET, AbstractRaplaLocale.CSV_CHARSET_DEFAULT),
                        preferences.getEntryAsString(AbstractRaplaLocale.HTML_CHARSET, AbstractRaplaLocale.HTML_CHARSET_DEFAULT),
                        preferences.getEntryAsInteger(ClientFacade.REFRESH_INTERVAL_ENTRY, ClientFacade.REFRESH_INTERVAL_DEFAULT)
                );
            }
            catch (Exception inner) {
                throw new RaplaException(inner);
            }
        }
        calendarName.setText(sys.title());

        try {
            String timezoneId = (sys.timezone() == null || sys.timezone().isEmpty())
                    ? timezoneService.getDefaultTimezone()
                    : sys.timezone();
            cboTimezone.setSelectedItem(timezoneId);
            csvCharset.setSelectedItem(sys.csvCharset());
            htmlCharset.setSelectedItem(sys.htmlCharset());

            String localeId = sys.locale();
            if (localeId != null && !localeId.isEmpty()) {
                Locale locale = LocaleTools.getLocale(localeId);
                languageChooser.setSelectedLanguage(locale.getLanguage());
                if (locale.getCountry() != null)
                {
                    countryChooser.setSelectedCountry(locale.getCountry());
                }
            }
            else {
                languageChooser.setSelectedLanguage(null);
            }
        }
        catch (Exception ex)
        {
            throw new RaplaException(ex);
        }

        seconds.setNumber(Long.valueOf(sys.refreshIntervalMs() / 1000));
        seconds.setEnabled(isRestartPossible);
    }

    public void commit() {
        String title = calendarName.getText();
        if ( title.trim().length() > 0)
        {
            preferences.putEntry( AbstractRaplaLocale.TITLE,title );
        }
        else
        {
            preferences.putEntry( AbstractRaplaLocale.TITLE, null);
        }
        
    	String timeZoneId = String.valueOf(cboTimezone.getSelectedItem());
    	preferences.putEntry( AbstractRaplaLocale.TIMEZONE, timeZoneId);

        String lang = languageChooser.getSelectedLanguage();
        if ( lang == null)
        {
            preferences.putEntry( AbstractRaplaLocale.LOCALE, null);
        }
        else {
            String localeId = lang + "_" + countryChooser.getSelectedCountry();
            preferences.putEntry( AbstractRaplaLocale.LOCALE, localeId);
        }
    	int delay = seconds.getNumber().intValue() * 1000;
    	preferences.putEntry( ClientFacade.REFRESH_INTERVAL_ENTRY, delay );
        Object selectedItem = htmlCharset.getSelectedItem();
        if (selectedItem != null)
        {
            String string = selectedItem.toString();
            if (!preferences.getEntryAsString(AbstractRaplaLocale.HTML_CHARSET, AbstractRaplaLocale.HTML_CHARSET_DEFAULT).equals( string ))
            {
                preferences.putEntry(AbstractRaplaLocale.HTML_CHARSET, string);
            }
        }
        selectedItem = csvCharset.getSelectedItem();
        if (selectedItem != null)
        {
            String string = selectedItem.toString();
            if (!preferences.getEntryAsString(AbstractRaplaLocale.CSV_CHARSET, AbstractRaplaLocale.CSV_CHARSET_DEFAULT).equals( string ))
            {
                preferences.putEntry(AbstractRaplaLocale.CSV_CHARSET, string);
            }
        }
         preferences.getEntryAsString(AbstractRaplaLocale.HTML_CHARSET, AbstractRaplaLocale.HTML_CHARSET_DEFAULT);

    }
    
	/**
	 * Gets all the iCal4J supported TimeZones from the Resource File They are
	 * generated by trial-and error in the BUILD event.
	 * 
	 * @return String[] of the TimeZones for direct use in the ComboBox
	 * @throws RaplaException 
	 */
	private String[] getTimeZonesFromResource() throws RaplaException 
	{
		try
		{
			List<String> zoneString = timezoneService.getICalTimezones();
			return zoneString.toArray(new String[] {});
		}
		catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }
	    	

	}


}
