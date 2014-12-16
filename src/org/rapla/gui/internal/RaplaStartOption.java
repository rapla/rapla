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

import org.rapla.RaplaMainContainer;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.UpdateModule;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.OptionPanel;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.plugin.export2ical.ICalTimezones;

public class RaplaStartOption extends RaplaGUIComponent implements OptionPanel {
    JPanel panel = new JPanel();
    JTextField calendarName;
    Preferences preferences;
	private JComboBox cboTimezone;
	ICalTimezones timezoneService;
	private JCheckBox ownReservations;
	RaplaNumber seconds = new RaplaNumber(new Double(10),new Double(10),null, false);
	
    public RaplaStartOption(RaplaContext context, ICalTimezones timezoneService) throws RaplaException {
        super( context );
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout(new double[][] {{pre, 5,pre, 5, pre}, {pre,5,pre, 5 , pre, 5, pre}}));
        this.timezoneService = timezoneService;      
        calendarName = new JTextField();
        addCopyPaste( calendarName);
        calendarName.setColumns(20);
        panel.add( new JLabel(getString("custom_applicationame")),"0,0"  );
        panel.add( calendarName,"2,0");
        calendarName.setEnabled(true);
    	String[] timeZoneIDs = getTimeZonesFromResource();
		panel.add(new JLabel(getString("timezone")), "0,2");
		@SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(timeZoneIDs);
		cboTimezone = jComboBox;
		panel.add(cboTimezone, "2,2");
		cboTimezone.setEditable(false);
		
		panel.add(new JLabel( getString("defaultselection") + " '" + getString("only_own_reservations") +"'"), "0,4");
		ownReservations = new JCheckBox();
		panel.add(ownReservations, "2,4");
		
		seconds.getNumberField().setBlockStepSize( 60);
	    seconds.getNumberField().setStepSize( 10);
	    
        panel.add( new JLabel(getString("seconds")),"4,6"  );
        panel.add( seconds,"2,6");
        panel.add( new JLabel(getString("connection") + ": " + getI18n().format("interval.format", "","")),"0,6"  );
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
        String name = preferences.getEntryAsString( RaplaMainContainer.TITLE,"");
        calendarName.setText(name);
        
    	try {
    		String timezoneId = preferences.getEntryAsString( RaplaMainContainer.TIMEZONE,timezoneService.getDefaultTimezone().get());
			cboTimezone.setSelectedItem(timezoneId);
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
            preferences.putEntry( RaplaMainContainer.TITLE,title );
        }
        else
        {
            preferences.putEntry( RaplaMainContainer.TITLE, (String)null);
        }
        
    	String timeZoneId = String.valueOf(cboTimezone.getSelectedItem());
    	preferences.putEntry( RaplaMainContainer.TIMEZONE, timeZoneId);
   
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
