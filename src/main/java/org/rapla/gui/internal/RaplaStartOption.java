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
package org.rapla.gui.internal;

import java.util.List;
import java.util.Locale;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.rapla.RaplaResources;
import org.rapla.client.internal.LanguageChooser;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.UpdateModule;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.gui.OptionPanel;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.plugin.export2ical.ICalTimezones;

public class RaplaStartOption extends RaplaGUIComponent implements OptionPanel {
    JPanel panel = new JPanel();
    JTextField calendarName;
    Preferences preferences;
	private JComboBox cboTimezone;
    private LanguageChooser languageChooser;
    private LanguageChooser countryChooser;
	ICalTimezones timezoneService;
	private JCheckBox ownReservations;
	RaplaNumber seconds = new RaplaNumber(new Double(10),new Double(10),null, false);

    @Override
    public RaplaResources getI18n()
    {
        return (RaplaResources) super.getI18n();
    }


    public RaplaStartOption(RaplaContext context, ICalTimezones timezoneService) throws RaplaException {
        super(context);
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout(new double[][] {{pre, 5,pre, 5, pre}, {pre,5,pre, 5 , pre, 5, pre,5 , pre, 5, pre}}));
        this.timezoneService = timezoneService;      
        calendarName = new JTextField();
        addCopyPaste(calendarName);
        calendarName.setColumns(20);
        panel.add(new JLabel(getString("custom_applicationame")), "0,0");
        panel.add(calendarName, "2,0");
        calendarName.setEnabled(true);
    	String[] timeZoneIDs = getTimeZonesFromResource();
		panel.add(new JLabel(getString("timezone")), "0,2");
		@SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(timeZoneIDs);
		cboTimezone = jComboBox;
		panel.add(cboTimezone, "2,2");
		cboTimezone.setEditable(false);

        languageChooser = new LanguageChooser(getLogger(),context);
        RaplaResources i18n = getI18n();
        panel.add( new JLabel(i18n.getString("server.language") ), "0,4");
        panel.add( languageChooser.getComponent(), "2,4");

        countryChooser = new LanguageChooser(getLogger(),context);
        panel.add( new JLabel(i18n.getString("server.country") ), "0,6");
        panel.add( countryChooser.getComponent(), "2,6");

        panel.add(new JLabel( getString("defaultselection") + " '" + getString("only_own_reservations") +"'"), "0,8");
		ownReservations = new JCheckBox();
		panel.add(ownReservations, "2,8");
		
		seconds.getNumberField().setBlockStepSize( 60);
	    seconds.getNumberField().setStepSize( 10);
	    
        panel.add( new JLabel(getString("seconds")),"4,10"  );
        panel.add( seconds,"2,10");
        panel.add( new JLabel(getString("connection") + ": " + getI18n().format("interval.format", "","")),"0,10"  );
        addCopyPaste( seconds.getNumberField());
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
        String name = preferences.getEntryAsString( ContainerImpl.TITLE,"");
        calendarName.setText(name);
        
    	try {
    		String timezoneId = preferences.getEntryAsString( ContainerImpl.TIMEZONE,timezoneService.getDefaultTimezone().get());
			cboTimezone.setSelectedItem(timezoneId);

            String localeId = preferences.getEntryAsString( ContainerImpl.LOCALE,null);
            if ( localeId != null) {
                Locale locale = DateTools.getLocale(localeId);
                languageChooser.setSelectedLanguage(locale.getLanguage());
            }
            else {
                languageChooser.setSelectedLanguage(null);
            }
		}
		catch (RaplaException ex)
		{
			throw ex;
		}
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }

        boolean selected= preferences.getEntryAsBoolean( CalendarModel.ONLY_MY_EVENTS_DEFAULT, true); 
        ownReservations.setSelected( selected);
        int delay = preferences.getEntryAsInteger( UpdateModule.REFRESH_INTERVAL_ENTRY, UpdateModule.REFRESH_INTERVAL_DEFAULT);
        seconds.setNumber( new Long(delay / 1000));
        seconds.setEnabled(getClientFacade().isClientForServer());

    }

    public void commit() {
        String title = calendarName.getText();
        if ( title.trim().length() > 0)
        {
            preferences.putEntry( ContainerImpl.TITLE,title );
        }
        else
        {
            preferences.putEntry( ContainerImpl.TITLE, (String)null);
        }
        
    	String timeZoneId = String.valueOf(cboTimezone.getSelectedItem());
    	preferences.putEntry( ContainerImpl.TIMEZONE, timeZoneId);

        String lang = languageChooser.getSelectedLanguage();
        if ( lang == null)
        {
            preferences.putEntry( ContainerImpl.LOCALE, null);
        }
        else {
            String localeId = lang;
            preferences.putEntry( ContainerImpl.LOCALE, localeId);
        }
        boolean selected= ownReservations.isSelected();
    	preferences.putEntry( CalendarModel.ONLY_MY_EVENTS_DEFAULT, selected); 
    	
    	int delay = seconds.getNumber().intValue() * 1000;
    	preferences.putEntry( UpdateModule.REFRESH_INTERVAL_ENTRY, delay );
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
			List<String> zoneString = timezoneService.getICalTimezones().get();
			return zoneString.toArray(new String[] {});
		}
		catch (RaplaException ex)
		{
			throw ex;
		}
	    catch (Exception ex)
	    {
	    	throw new RaplaException(ex);
	    }
	    	

	}


}
