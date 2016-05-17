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

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.SystemOptionPanel;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.calendar.RaplaTime;
import org.rapla.components.calendarview.WeekdayMapper;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.inject.ExtensionRepeatable;

@ExtensionRepeatable({
@Extension(provides = UserOptionPanel.class,id="calendarOption"),
@Extension(provides = SystemOptionPanel.class,id="calendarOption")
})
public class CalendarOption extends RaplaGUIComponent implements UserOptionPanel,SystemOptionPanel, DateChangeListener
{
    JPanel panel = new JPanel();
    JCheckBox showExceptionsField = new JCheckBox();
    @SuppressWarnings("unchecked")
	JComboBox colorBlocks = new JComboBox( new String[] {
    		 CalendarOptionsImpl.COLOR_NONE	
    		,CalendarOptionsImpl.COLOR_RESOURCES
    		, CalendarOptionsImpl.COLOR_EVENTS
    		, CalendarOptionsImpl.COLOR_EVENTS_AND_RESOURCES
    }
    													);
    RaplaNumber rowsPerHourField = new RaplaNumber(new Double(1),new Double(1),new Double(12), false);
    Preferences preferences;
    CalendarOptions options;
    RaplaTime worktimeStart;
    RaplaTime worktimeEnd;
    JPanel excludeDaysPanel =  new JPanel();
    JCheckBox[] box = new JCheckBox[7];
    WeekdayMapper mapper;
    RaplaNumber nTimesField = new RaplaNumber(new Double(1),new Double(1),new Double(365), false);

    @SuppressWarnings({ "unchecked" })
    JComboBox nonFilteredEvents = new JComboBox( new String[]
            {
                CalendarOptionsImpl.NON_FILTERED_EVENTS_TRANSPARENT,
                CalendarOptionsImpl.NON_FILTERED_EVENTS_HIDDEN
            }
            );
    JLabel worktimeEndError;
    
    @SuppressWarnings({ "unchecked" })
    JComboBox minBlockWidth = new JComboBox( new Integer[] {0,50,100,200});
    JComboBox firstDayOfWeek;
    RaplaNumber daysInWeekview;

    @Inject
    public CalendarOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface ioInterface) {
        super(facade, i18n, raplaLocale, logger);
        daysInWeekview = new RaplaNumber(7, 3, 35, false);
        mapper = new WeekdayMapper(getLocale());
        worktimeStart = createRaplaTime(ioInterface);
        worktimeStart.setRowsPerHour( 1 );
        worktimeEnd = createRaplaTime(ioInterface);
        worktimeEnd.setRowsPerHour( 1 );
        double pre = TableLayout.PREFERRED;
        double fill = TableLayout.FILL;
        // rows = 8 columns = 4
        panel.setLayout( new TableLayout(new double[][] {{pre, 5, pre, 5 , pre, 5, pre}, {pre,5,pre,5,pre,5,pre,5,pre,5,pre,5,pre,5,pre,5,pre,5,pre,5,pre,5,pre,5,pre,5,fill}}));

        panel.add( new JLabel(getString("rows_per_hour")),"0,0"  );
        panel.add( rowsPerHourField,"2,0");
        panel.add( new JLabel(getString("start_time")),"0,2"  );
        JPanel worktimeStartPanel = new JPanel();
        worktimeStartPanel.add( worktimeStart);
        panel.add( worktimeStartPanel, "2,2,l");
        panel.add( new JLabel(getString("end_time")),"0,4"  );
        worktimeStart.addDateChangeListener(this);
        JPanel worktimeEndPanel = new JPanel();
        panel.add( worktimeEndPanel,"2,4,l");
        worktimeEndPanel.add( worktimeEnd);
        worktimeEndError =  new JLabel(getString("appointment.next_day"));
        worktimeEndPanel.add( worktimeEndError);
        worktimeEnd.addDateChangeListener(this);
        panel.add( new JLabel(getString("color")),"0,6"  );
        panel.add( colorBlocks,"2,6");

        showExceptionsField.setText("");        
        panel.add( new JLabel(getString("display_exceptions")),"0,8");
        panel.add( showExceptionsField,"2,8");
        
        panel.add( new JLabel(getString("events_not_matched_by_filter")),"0,10");
        panel.add( nonFilteredEvents,"2,10");
        setRenderer();
        
        @SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(mapper.getNames());
		firstDayOfWeek = jComboBox;
        panel.add( new JLabel(getString("day1week")),"0,12");
        panel.add( firstDayOfWeek,"2,12");

        panel.add( new JLabel(getString("daysInWeekview")),"0,14");
        panel.add( daysInWeekview,"2,14");
        
        panel.add( new JLabel(getString("minimumBlockWidth")),"0,16");
        JPanel minWidthContainer = new JPanel();
        minWidthContainer.setLayout( new FlowLayout(FlowLayout.LEFT));
        minWidthContainer.add(minBlockWidth);
        minWidthContainer.add(new JLabel("%"));
        
        panel.add( minWidthContainer,"2,16");

        panel.add( new JLabel(getString("exclude_days")),"0,22,l,t");
        panel.add( excludeDaysPanel,"2,22");
        excludeDaysPanel.setLayout( new BoxLayout( excludeDaysPanel,BoxLayout.Y_AXIS));
        for ( int i=0;i<box.length;i++) {
            int weekday = mapper.dayForIndex( i);
            box[i] = new JCheckBox(mapper.getName(weekday));
            excludeDaysPanel.add( box[i]);
            
        }
    }
    
    @Override
    public boolean isEnabled()
    {
        return true;
    }

	@SuppressWarnings("unchecked")
	private void setRenderer() {
		ListRenderer listRenderer = new ListRenderer();
        nonFilteredEvents.setRenderer( listRenderer );
        colorBlocks.setRenderer( listRenderer );
	}

    public JComponent getComponent() {
        return panel;
    }
    public String getName(Locale locale) {
        return getString("calendar");
    }

    public void setPreferences( Preferences preferences) {
        this.preferences = preferences;
    }

    public void show() throws RaplaException {
    	// get the options 
        RaplaConfiguration config = preferences.getEntry( CalendarOptionsImpl.CALENDAR_OPTIONS);
        if ( config != null) {
            options = new CalendarOptionsImpl( config );
        } else {
            options = getCalendarOptions();
        }

        if ( options.isEventColoring() && options.isResourceColoring())
        {
        	colorBlocks.setSelectedItem(  CalendarOptionsImpl.COLOR_EVENTS_AND_RESOURCES);
        }
        else if ( options.isEventColoring() )
        {
        	colorBlocks.setSelectedItem(  CalendarOptionsImpl.COLOR_EVENTS);
        }
        else if (  options.isResourceColoring())
        {
        	colorBlocks.setSelectedItem(  CalendarOptionsImpl.COLOR_RESOURCES);
        }
        else
        {
          	colorBlocks.setSelectedItem(  CalendarOptionsImpl.COLOR_NONE);
        } 
        
        showExceptionsField.setSelected( options.isExceptionsVisible());
        
        rowsPerHourField.setNumber( new Long(options.getRowsPerHour()));
        
        int workTime = options.getWorktimeStartMinutes();
        worktimeStart.setTime(  new Date(DateTools.toTime(workTime / 60, workTime % 60, 0)));
        workTime = options.getWorktimeEndMinutes();
        worktimeEnd.setTime(  new Date(DateTools.toTime(workTime / 60, workTime % 60, 0)));
        
        for ( int i=0;i<box.length;i++) {
            int weekday = mapper.dayForIndex( i);
            box[i].setSelected( options.getExcludeDays().contains( new Integer( weekday)));
        }
        int firstDayOfWeek2 = options.getFirstDayOfWeek();
        firstDayOfWeek.setSelectedIndex( mapper.indexForDay( firstDayOfWeek2));
        
        daysInWeekview.setNumber( options.getDaysInWeekview());
        minBlockWidth.setSelectedItem( new Integer(options.getMinBlockWidth()));
        nonFilteredEvents.setSelectedItem( options.isNonFilteredEventsVisible() ? CalendarOptionsImpl.NON_FILTERED_EVENTS_TRANSPARENT : CalendarOptionsImpl.NON_FILTERED_EVENTS_HIDDEN);
    }

    public void commit() {
    	// Save the options
    	RaplaConfiguration calendarOptions = new RaplaConfiguration("calendar-options");
        DefaultConfiguration worktime = new DefaultConfiguration(CalendarOptionsImpl.WORKTIME);
        DefaultConfiguration excludeDays = new DefaultConfiguration(CalendarOptionsImpl.EXCLUDE_DAYS);
        DefaultConfiguration rowsPerHour = new DefaultConfiguration(CalendarOptionsImpl.ROWS_PER_HOUR);
        DefaultConfiguration exceptionsVisible = new DefaultConfiguration(CalendarOptionsImpl.EXCEPTIONS_VISIBLE);
        
        DefaultConfiguration daysInWeekview = new DefaultConfiguration(CalendarOptionsImpl.DAYS_IN_WEEKVIEW);
        DefaultConfiguration firstDayOfWeek = new DefaultConfiguration(CalendarOptionsImpl.FIRST_DAY_OF_WEEK);
        DefaultConfiguration minBlockWidth = new DefaultConfiguration(CalendarOptionsImpl.MIN_BLOCK_WIDTH);
        
        daysInWeekview.setValue( this.daysInWeekview.getNumber().intValue());
        int selectedIndex = this.firstDayOfWeek.getSelectedIndex();
		int weekday = mapper.dayForIndex(selectedIndex);
        firstDayOfWeek.setValue( weekday);
        DefaultConfiguration colorBlocks = new DefaultConfiguration(CalendarOptionsImpl.COLOR_BLOCKS);
        String colorValue = (String) this.colorBlocks.getSelectedItem();
        if ( colorValue != null )
        {
            colorBlocks.setValue(  colorValue );
        }
        calendarOptions.addChild( colorBlocks );
        final DateTools.TimeWithoutTimezone startTime = DateTools.toTime(worktimeStart.getTime().getTime());
        int worktimeStartHour = startTime.hour;
        int worktimeStartMinute = startTime.minute;

        final DateTools.TimeWithoutTimezone endTime = DateTools.toTime(worktimeEnd.getTime().getTime());
        int worktimeEndHour = endTime.hour;
        int worktimeEndMinute = endTime.minute;
        if ( worktimeStartMinute > 0 || worktimeEndMinute > 0)
        {
        	worktime.setValue(  worktimeStartHour + ":" + worktimeStartMinute + "-" + worktimeEndHour + ":" + worktimeEndMinute );
        }
        else
        {
          	worktime.setValue(  worktimeStartHour + "-" + worktimeEndHour );
                  	
        }
        calendarOptions.addChild( worktime);

        exceptionsVisible.setValue( showExceptionsField.isSelected() );
        calendarOptions.addChild( exceptionsVisible);

        rowsPerHour.setValue( rowsPerHourField.getNumber().intValue());
        StringBuffer days = new StringBuffer();
        for ( int i=0;i<box.length;i++) {
            if (box[i].isSelected()) {
                if ( days.length() > 0)
                    days.append(",");
                days.append( mapper.dayForIndex( i ));
            }
        }
        calendarOptions.addChild( rowsPerHour);
        excludeDays.setValue( days.toString());
        calendarOptions.addChild( excludeDays);
        calendarOptions.addChild(daysInWeekview);
        calendarOptions.addChild(firstDayOfWeek);
        Object selectedItem = this.minBlockWidth.getSelectedItem();
        if ( selectedItem != null)
        {
        	minBlockWidth.setValue( (Integer) selectedItem);
        	calendarOptions.addChild(minBlockWidth);
            
        }
        
        DefaultConfiguration nonFilteredEventsConfig = new DefaultConfiguration(CalendarOptionsImpl.NON_FILTERED_EVENTS);
        nonFilteredEventsConfig.setValue( nonFilteredEvents.getSelectedItem().toString());
        calendarOptions.addChild( nonFilteredEventsConfig);
        
        preferences.putEntry( CalendarOptionsImpl.CALENDAR_OPTIONS, calendarOptions);
	}

	public void dateChanged(DateChangeEvent evt) {
        final DateTools.TimeWithoutTimezone startTime = DateTools.toTime(worktimeEnd.getTime().getTime());
        int worktimeS = startTime.hour*60  + startTime.minute;
        final DateTools.TimeWithoutTimezone endTime = DateTools.toTime(worktimeEnd.getTime().getTime());
        int worktimeE = endTime.hour * 60 + endTime.minute;
        worktimeE = (worktimeE == 0)?24*60:worktimeE;
        boolean overnight = worktimeS >= worktimeE|| worktimeE == 24*60;
		worktimeEndError.setVisible( overnight);
	}

	private class ListRenderer extends DefaultListCellRenderer  {
		private static final long serialVersionUID = 1L;
		
		public Component getListCellRendererComponent(
		        //@SuppressWarnings("rawtypes") 
		        JList list
		        ,Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if ( value != null) {
                setText(getString(  value.toString()));
            }
            return this;
        }
	}	
}