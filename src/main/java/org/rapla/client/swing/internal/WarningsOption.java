/*--------------------------------------------------------------------------*
 | Copyright (C) 2023 Christopher Kohlhaas                                  |
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
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.Locale;

@Extension(provides = UserOptionPanel.class,id="warningOption")
public class WarningsOption extends RaplaGUIComponent implements UserOptionPanel
{
    JPanel panel = new JPanel();
    Preferences preferences;
    JCheckBox showConflictWarningsField = new JCheckBox();
    JCheckBox showNotInCalendarWarningsField = new JCheckBox();
    JCheckBox showAbortEditWarningsField = new JCheckBox();

    JCheckBox showHolidayWarnings = new JCheckBox();

    JCheckBox showHolidayWarningsSingleAppointment = new JCheckBox();

    final boolean isHolidayEnabled;
    @Inject
    public WarningsOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        super(facade, i18n, raplaLocale, logger);
        showConflictWarningsField.setText("");        
        double pre = TableLayout.PREFERRED;
        panel.setLayout( new TableLayout(new double[][] {{pre, 5,pre}, {pre,5,pre,5,pre,5,pre}}));
        panel.add( new JLabel(getString("warning.conflict")),"0,0");
        panel.add( showConflictWarningsField,"2,0");
        panel.add( new JLabel(getString("warning.not_in_calendar_option")),"0,2");
        panel.add( showNotInCalendarWarningsField,"2,2");
        panel.add( new JLabel(getString("warning.confirm-abort")),"0,4");
        panel.add( showAbortEditWarningsField,"2,4");
        isHolidayEnabled = isHolidayEnabled(facade);
        if (isHolidayEnabled)
        {
            panel.add(new JLabel(getString("warning.repeating_conflicts_with_holiday")), "0,6");
            panel.add(showHolidayWarnings, "2,6");
            panel.add(new JLabel(getString("warning.single_conflicts_with_holiday")), "0,8");
            panel.add(showHolidayWarningsSingleAppointment, "2,8");
        }
    }

    protected boolean isHolidayEnabled(ClientFacade facade)
    {
        try
        {
            final PeriodModel holiday = PeriodModel.getHoliday(facade.getRaplaFacade());
            return holiday != null;
        }
        catch (RaplaException e)
        {
            return false;
        }
    }

    @Override
    public boolean isEnabled()
    {
        return true;
    }

    public JComponent getComponent() {
        return panel;
    }
    public String getName(Locale locale) {
        return getString("warnings");
    }

    public void setPreferences( Preferences preferences) {
        this.preferences = preferences;
    }

    public void show() throws RaplaException {
        // get the options
        {
            boolean config = preferences.getEntryAsBoolean( CalendarOptionsImpl.SHOW_CONFLICT_WARNING, true);
            showConflictWarningsField.setSelected( config);
        }
        {
            boolean config = preferences.getEntryAsBoolean( CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING, true);
            showNotInCalendarWarningsField.setSelected( config);
        }
        {
            boolean config = preferences.getEntryAsBoolean( CalendarOptionsImpl.SHOW_ABORT_EDIT_WARNING, true);
            showAbortEditWarningsField.setSelected( config);
        }
        if (isHolidayEnabled)
        {
            boolean config = preferences.getEntryAsBoolean( CalendarOptionsImpl.SHOW_HOLIDAY_WARNING, true);
            showHolidayWarnings.setSelected( config);

            boolean configSingle = preferences.getEntryAsBoolean( CalendarOptionsImpl.SHOW_HOLIDAY_WARNING_SINGLE_APPOINTMENT, true);
            showHolidayWarningsSingleAppointment.setSelected( configSingle);
        }
    }

    public void commit() {
        // Save the options

        {
            boolean selected = showConflictWarningsField.isSelected();
            preferences.putEntry( CalendarOptionsImpl.SHOW_CONFLICT_WARNING, selected);
        }
        {
            boolean selected = showNotInCalendarWarningsField.isSelected();
            preferences.putEntry( CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING, selected);
        }
        {
            boolean selected = showAbortEditWarningsField.isSelected();
            preferences.putEntry( CalendarOptionsImpl.SHOW_ABORT_EDIT_WARNING, selected);
        }

        if (isHolidayEnabled)
        {
            boolean selected = showHolidayWarnings.isSelected();
            preferences.putEntry( CalendarOptionsImpl.SHOW_HOLIDAY_WARNING, selected);

            boolean singleSelected = showHolidayWarningsSingleAppointment.isSelected();
            preferences.putEntry( CalendarOptionsImpl.SHOW_HOLIDAY_WARNING_SINGLE_APPOINTMENT, singleSelected);


        }
    }


}