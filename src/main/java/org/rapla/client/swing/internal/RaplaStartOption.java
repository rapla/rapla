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
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.export2ical.ICalTimezones;
import org.rapla.storage.RemoteLocaleService;
import org.rapla.storage.dbrm.RestartServer;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Locale;

@Extension(provides = SystemOptionPanel.class, id="startOption")
public class RaplaStartOption extends RaplaGUIComponent implements SystemOptionPanel {
    JPanel panel = new JPanel();
    JTextField calendarName;
    Preferences preferences;
	private JComboBox cboTimezone;
    private LanguageChooser languageChooser;
    private CountryChooser countryChooser;
	ICalTimezones timezoneService;
	private JCheckBox ownReservations;
	RaplaNumber seconds = new RaplaNumber(new Double(10),new Double(10),null, false);
    boolean isRestartPossible;

    @Override
    public RaplaResources getI18n()
    {
        return super.getI18n();
    }


    @Inject
    public RaplaStartOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ICalTimezones timezoneService, RemoteLocaleService localeService, IOInterface ioInterface, RestartServer restartServer) throws
            RaplaInitializationException {
        super(facade, i18n, raplaLocale, logger);
        isRestartPossible = restartServer.isRestartPossible();
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout(new double[][] {{pre, 5,pre, 5, pre}, {pre,5,pre, 5 , pre, 5, pre,5 , pre, 5, pre}}));
        this.timezoneService = timezoneService;      
        calendarName = new JTextField();
        addCopyPaste(calendarName, i18n, raplaLocale, ioInterface, logger);
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
		panel.add(cboTimezone, "2,2");
		cboTimezone.setEditable(false);

        languageChooser = new LanguageChooser(getLogger(),i18n,raplaLocale);
        panel.add( new JLabel(i18n.getString("server.language") ), "0,4");
        panel.add( languageChooser.getComponent(), "2,4");

        countryChooser = new CountryChooser(getLogger(),i18n,raplaLocale,localeService);
        panel.add( new JLabel(i18n.getString("server.country") ), "0,6");
        panel.add( countryChooser.getComponent(), "2,6");
        languageChooser.addActionListener(e -> countryChooser.changeLanguage(languageChooser.getSelectedLanguage()));

        panel.add(new JLabel( getString("defaultselection") + " '" + getString("only_own_reservations") +"'"), "0,8");
		ownReservations = new JCheckBox();
		panel.add(ownReservations, "2,8");
		
		seconds.getNumberField().setBlockStepSize( 60);
	    seconds.getNumberField().setStepSize( 10);
	    
        panel.add( new JLabel(getString("seconds")),"4,10"  );
        panel.add( seconds,"2,10");
        panel.add( new JLabel(getString("connection") + ": " + getI18n().format("interval.format", "","")),"0,10"  );
        addCopyPaste( seconds.getNumberField(), i18n, raplaLocale, ioInterface, logger);
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
        String name = preferences.getEntryAsString( AbstractRaplaLocale.TITLE,"");
        calendarName.setText(name);
        
    	try {
    		String timezoneId = preferences.getEntryAsString( AbstractRaplaLocale.TIMEZONE,timezoneService.getDefaultTimezone());
			cboTimezone.setSelectedItem(timezoneId);

            String localeId = preferences.getEntryAsString( AbstractRaplaLocale.LOCALE,null);
            if ( localeId != null) {
                Locale locale = LocaleTools.getLocale(localeId);
                languageChooser.setSelectedLanguage(locale.getLanguage());
                if(locale.getCountry() != null)
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

        boolean selected= preferences.getEntryAsBoolean( CalendarModel.ONLY_MY_EVENTS_DEFAULT, true); 
        ownReservations.setSelected( selected);
        int delay = preferences.getEntryAsInteger( ClientFacade.REFRESH_INTERVAL_ENTRY, ClientFacade.REFRESH_INTERVAL_DEFAULT);
        seconds.setNumber( new Long(delay / 1000));
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
        boolean selected= ownReservations.isSelected();
    	preferences.putEntry( CalendarModel.ONLY_MY_EVENTS_DEFAULT, selected); 
    	
    	int delay = seconds.getNumber().intValue() * 1000;
    	preferences.putEntry( ClientFacade.REFRESH_INTERVAL_ENTRY, delay );
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
