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
package org.rapla.plugin.timeslot.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.calendar.RaplaTime;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.timeslot.Timeslot;
import org.rapla.plugin.timeslot.TimeslotPlugin;
import org.rapla.plugin.timeslot.TimeslotProvider;

import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Extension(provides = PluginOptionPanel.class,id = TimeslotPlugin.PLUGIN_ID)
public class TimeslotOption extends RaplaGUIComponent implements PluginOptionPanel
{
	protected JComponent container;
	JPanel list = new JPanel();
	List<Timeslot> timeslots;
    private final TimeslotProvider timeslotProvider;
    private final IOInterface ioInterface;
    private Preferences preferences;
	
    class TimeslotRow
    {
    	RaplaTime raplatime;
    	JTextField textfield = new JTextField();
    	RaplaButton delete = new RaplaButton(RaplaButton.SMALL);

    	public TimeslotRow(Timeslot slot) 
    	{
    	    addCopyPaste( textfield, getI18n(), getRaplaLocale(), ioInterface, getLogger());
    		textfield.setText( slot.getName());
    		int minuteOfDay = slot.getMinuteOfDay();
    		int hour = minuteOfDay /60;
			int minute = minuteOfDay %60;
			RaplaLocale raplaLocale = getRaplaLocale();
			raplatime = new RaplaTime(raplaLocale.getLocale(), IOUtil.getTimeZone());
			raplatime.setTime(hour, minute);
			setIcon(delete,i18n.getIcon("icon.remove"));
			delete.addActionListener(arg0 -> {
                rows.remove( TimeslotRow.this);
                update();
            });
    	}
    	
    	
    }

	@Inject
    public TimeslotOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TimeslotProvider timeslotProvider,  IOInterface ioInterface)
    {
        super(facade, i18n, raplaLocale, logger);
        this.timeslotProvider = timeslotProvider;
        this.ioInterface = ioInterface;
    }


	@Override
	public void setPreferences(Preferences preferences)
	{
		this.preferences = preferences;
	}

	public void setIcon(JButton button, I18nIcon icon)
	{
		button.setIcon(RaplaImages.getIcon( icon));
	}

	List<TimeslotRow> rows = new ArrayList<>();
    protected JPanel createPanel() throws RaplaException
    {

    	JScrollPane jScrollPane = new JScrollPane(list);
        JPanel container = new JPanel();
        container.setLayout( new BorderLayout());
        container.add(jScrollPane,BorderLayout.CENTER);
        JPanel header = new JPanel();
        RaplaButton reset = new RaplaButton(RaplaButton.SMALL);
    	RaplaButton resetButton = reset;
		setIcon(resetButton,i18n.getIcon("icon.remove"));
		resetButton.setText(getString("reset"));
		RaplaButton newButton = new RaplaButton(RaplaButton.SMALL);
		setIcon(newButton,i18n.getIcon("icon.new"));
		newButton.setText(getString("new"));


		header.add( newButton);
		header.add( resetButton );
		newButton.addActionListener(arg0 -> {
            int minuteOfDay = 0;
            String lastName = "";
            Timeslot slot = new Timeslot(lastName, minuteOfDay);
            rows.add( new TimeslotRow(slot));
            update();
        });
		
		resetButton.addActionListener(arg0 -> {
            timeslots = TimeslotProvider.getDefaultTimeslots(getRaplaLocale());
            initRows();
            update();
        });
        container.add(header,BorderLayout.NORTH);
        
		return container;
    }

	@Override
	public JComponent getComponent()
	{
		return container;
	}

	protected void initRows()  {
		rows.clear();
    	for ( Timeslot slot:timeslots)
    	{
    		TimeslotRow row =  new TimeslotRow( slot);
    		rows.add( row);
    	}
    	TimeslotRow firstRow = rows.get(0);
    	firstRow.delete.setEnabled( false);
    	firstRow.raplatime.setEnabled( false);
    	list.removeAll();
	}


	protected void update() {
		timeslots = mapToTimeslots();
		list.removeAll();
    	TableLayout tableLayout = new TableLayout();
    	list.setLayout(tableLayout);
    	tableLayout.insertColumn(0,TableLayout.PREFERRED);
    	tableLayout.insertColumn(1,10);
    	tableLayout.insertColumn(2,TableLayout.PREFERRED);
    	tableLayout.insertColumn(3,10);
    	tableLayout.insertColumn(4,TableLayout.FILL);

    	list.setLayout( tableLayout);
    	tableLayout.insertRow(0, TableLayout.PREFERRED);
    	list.add(new JLabel("time"),"2,0");
    	list.add(new JLabel("name"),"4,0");
    	int i = 0;
    	for ( TimeslotRow row:rows)
    	{
    		tableLayout.insertRow(++i, TableLayout.MINIMUM);
    	 	list.add(row.delete,"0,"+i);
    		list.add(row.raplatime,"2,"+i);
        	list.add(row.textfield,"4,"+i);
    	}
    	list.validate();
    	list.repaint();
    	container.validate();
    	container.repaint();
	}

    
    protected void addChildren( DefaultConfiguration newConfig) 
    {
      	for ( Timeslot slot: timeslots)
    	{
    		DefaultConfiguration conf = new DefaultConfiguration("timeslot");
    		conf.setAttribute("name", slot.getName());
    		int minuteOfDay = slot.getMinuteOfDay();
    		SerializableDateTimeFormat format = getRaplaLocale().getSerializableFormat();
			final long l = DateTools.toTime(minuteOfDay / 60, minuteOfDay % 60, 0);
			String time = format.formatTime(new Date(l));
			conf.setAttribute("time", time);
    		newConfig.addChild( conf);
    	}
      	
      	try {
			timeslotProvider.update(newConfig);
		} catch (ParseDateException e) {
			getLogger().error(e.getMessage());
      	}
    }


	protected List<Timeslot> mapToTimeslots() {
		List<Timeslot> timeslots = new ArrayList<>();
  
      	for ( TimeslotRow row: rows)
    	{
    		
    		String name = row.textfield.getText();
    		RaplaTime raplatime = row.raplatime;
			Date time = raplatime.getTime();
    	   	if ( time != null )
    		{
				int minuteOfDay = DateTools.getMinuteOfDay(time.getTime());
    			timeslots.add( new Timeslot( name, minuteOfDay));
    		}
    	}
      	Collections.sort(timeslots);
		return timeslots;
	}

    protected void readConfig( Configuration config)   
    {
    	RaplaLocale raplaLocale = getRaplaLocale();
    	try {
			timeslots = TimeslotProvider.parseConfig(config, raplaLocale);
		} catch (ParseDateException e) {
		}
    	if ( timeslots == null)
    	{
			timeslots = TimeslotProvider.getDefaultTimeslots(raplaLocale);
    	}
    	initRows();
    	update();
    }

	public void show() throws RaplaException
	{
		container = createPanel();
		Configuration config = preferences.getEntry( TimeslotPlugin.CONFIG, null);
		readConfig( config );
	}

    public void commit() throws RaplaException {
    	timeslots = mapToTimeslots();
		RaplaConfiguration newConfig = new RaplaConfiguration("config" );
		addChildren( newConfig );
		preferences.putEntry( TimeslotPlugin.CONFIG,newConfig);
    }

    public String getName(Locale locale) {
        return "Timeslot Plugin";
    }

}
