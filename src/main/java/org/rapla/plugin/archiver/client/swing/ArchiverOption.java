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
package org.rapla.plugin.archiver.client.swing;

import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.scheduler.Subject;
import org.rapla.storage.dbrm.RestartServer;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

@Extension(provides = PluginOptionPanel.class,id=ArchiverService.PLUGIN_ID)
public class ArchiverOption  implements PluginOptionPanel,ActionListener  {

    JPanel content;
    RaplaNumber dayField = new RaplaNumber(Integer.valueOf(25), Integer.valueOf(0),null,false);
    JCheckBox removeOlderYesNo = new JCheckBox();
    JCheckBox exportToDataXML = new JCheckBox();
    RaplaButton deleteNow;
    RaplaButton backupButton; 
    RaplaButton restoreButton;
	ArchiverService archiver;
    Preferences preferences;
    Logger logger;
    RestartServer restartServer;
    Subject<String> busyIdleObservable;
    private final DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public ArchiverOption( Logger logger,ArchiverService archiver, RestartServer restartServer, DialogUiFactoryInterface dialogUiFactory, CommandScheduler scheduler){
        this.archiver = archiver;
        this.logger = logger;
        this.restartServer = restartServer;
        this.dialogUiFactory = dialogUiFactory;
        this.busyIdleObservable = scheduler.createPublisher();
    }


    @Override public Component getComponent()
    {
        return content;
    }

    @Override public void setPreferences(Preferences preferences)
    {
        new RaplaConfiguration();
        this.preferences = preferences;
    }

    @Override public void commit() throws RaplaException
    {
        RaplaConfiguration newConfig = new RaplaConfiguration("config");
        addChildren( newConfig );
        preferences.putEntry( ArchiverService.CONFIG, newConfig);
    }

    @Override public void show()
    {
        createPanel();
        RaplaConfiguration config = preferences.getEntry(ArchiverService.CONFIG, new RaplaConfiguration());
        readConfig(config);
    }


    protected void createPanel()  {
        content = new JPanel();
        double[][] sizes = new double[][] {
            {5,TableLayout.PREFERRED, 5,TableLayout.PREFERRED,5, TableLayout.PREFERRED}
            ,{TableLayout.PREFERRED,5,TableLayout.PREFERRED,15,TableLayout.PREFERRED,5,TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED}
        };
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
        //content.add(new JLabel("Remove old events"), "1,0");
        removeOlderYesNo.setText("Remove events older than");
        content.add( removeOlderYesNo, "1,0");
        //content.add(new JLabel("Older than"), "1,2");
        content.add( dayField, "3,0");
        content.add( new JLabel("days"), "5,0");
        deleteNow = new RaplaButton(RaplaButton.DEFAULT);
        deleteNow.setText("delete now");
        content.add(deleteNow, "3,2");
        
        exportToDataXML.setText("Export db to file");
        content.add(exportToDataXML, "1,4");
        
        backupButton = new RaplaButton(RaplaButton.DEFAULT);
        backupButton.setText("backup now");
        content.add( backupButton, "3,4");

        restoreButton = new RaplaButton(RaplaButton.DEFAULT);
        restoreButton.setText("restore and restart");
        content.add( restoreButton, "3,6");
  
        removeOlderYesNo.addActionListener( this );
        exportToDataXML.addActionListener( this );
        deleteNow.addActionListener( this );
        backupButton.addActionListener( this );
        restoreButton.addActionListener( this );
    }



    private void updateEnabled()
    {
    	{
    		boolean selected = removeOlderYesNo.isSelected();
			dayField.setEnabled( selected);
			deleteNow.setEnabled( selected );
    	}
        {
        	boolean selected = exportToDataXML.isSelected();
        	backupButton.setEnabled(selected);
        	restoreButton.setEnabled( selected);
        }
    }
    
        
    protected void addChildren( DefaultConfiguration newConfig) {
        if ( removeOlderYesNo.isSelected())
        {
            DefaultConfiguration conf = new DefaultConfiguration(ArchiverService.REMOVE_OLDER_THAN_ENTRY);
            conf.setValue(dayField.getNumber().intValue() );
            newConfig.addChild( conf );
        }
        if ( exportToDataXML.isSelected())
        {
            DefaultConfiguration conf = new DefaultConfiguration(ArchiverService.EXPORT);
            conf.setValue( true );
            newConfig.addChild( conf );
        }
    }

    protected void readConfig( Configuration config)   {
    	int days = config.getChild(ArchiverService.REMOVE_OLDER_THAN_ENTRY).getValueAsInteger(-20);
        boolean isEnabled = days != -20;
        removeOlderYesNo.setSelected( isEnabled );
        if ( days == -20 )
        {
            days = 30;
        }
        dayField.setNumber( Integer.valueOf(days));
    	boolean exportSelected= config.getChild(ArchiverService.EXPORT).getValueAsBoolean(false);
    	try {
			boolean exportEnabled = archiver.isExportEnabled();
			exportToDataXML.setEnabled( exportEnabled );
			exportToDataXML.setSelected( exportSelected && exportEnabled );
    	} catch (RaplaException e) {
    		exportToDataXML.setEnabled( false );
    		exportToDataXML.setSelected( false );
    		logger.error(e.getMessage(), e);
		}
        updateEnabled();
    }



    public String getName(Locale locale) {
        return "Archiver Plugin";
    }

	public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        final PopupContext popupContext = dialogUiFactory.createPopupContext(() -> content);
        if ( source == exportToDataXML || source == removeOlderYesNo)
        {
            updateEnabled();
        }
        else
        {
            final Promise<Void> result;
            content.setEnabled( false);
            if ( source == deleteNow)
            {
                Number days =  dayField.getNumber();
                if ( days != null)
                {
                    result = archiver.delete(Integer.valueOf(days.intValue()));
                }
                else
                {
                    result =ResolvedPromise.VOID_PROMISE;
                }
            }
            else if (source == backupButton)
            {
                result = archiver.backupNow();
            }
            else if (source == restoreButton)
            {
                DialogInterface dialog = dialogUiFactory.createTextDialog(popupContext, "Warning", "The current data will be overwriten by the backup version. Do you want to proceed?", new String[]{"restore data","abort"});
                dialog.setDefault( 1);
                result = dialog.start(true).thenCompose((index) -> (index == 0) ?
                        archiver.restore().thenCompose((dummy) -> restartServer.restartServer()) :
                        ResolvedPromise.VOID_PROMISE);
            }
            else
            {
                throw new IllegalStateException("Unknown button " + source);
            }
            result.exceptionally((ex) -> dialogUiFactory.showException(ex, popupContext))
                    .finally_(()->content.setEnabled( true));
        }
	}

}
