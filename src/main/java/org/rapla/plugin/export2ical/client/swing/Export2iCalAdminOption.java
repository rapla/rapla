package org.rapla.plugin.export2ical.client.swing;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import javax.inject.Inject;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.swing.DefaultPluginOption;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.ICalConfigService;

/*******************************************************************************
 * This is the admin-option panel
 * 
 * @author Twardon
 * 
 */
@Extension(provides = PluginOptionPanel.class,id= Export2iCalPlugin.PLUGIN_ID)
public class Export2iCalAdminOption extends DefaultPluginOption implements ActionListener {

	private JSpinner spiDaysBefore;
	private JSpinner spiDaysAfter;
	private JRadioButton optGlobalInterval;
	private JRadioButton optUserInterval;

	private JLabel lblLastModifiedInterval;
	private JSpinner spiLastModifiedInterval;
	private JCheckBox chkUseLastModifiedIntervall;
	private JCheckBox chkExportAttendees;
	private JTextArea txtEMailRessourceAttribute;
    private JComboBox cbDefaultParticipationsStatusRessourceAttribute;
    private ICalConfigService configService;
    private final IOInterface ioInterface;

	@Inject
    public Export2iCalAdminOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ICalConfigService configService, IOInterface ioInterface){
		super(facade, i18n, raplaLocale, logger);
		this.configService = configService;
        this.ioInterface = ioInterface;
	}

	protected JPanel createPanel() throws RaplaException {
		spiLastModifiedInterval = new JSpinner(new SpinnerNumberModel(5, 0, null, 1));
		chkUseLastModifiedIntervall = new JCheckBox("do not deliver new calendar");
		chkExportAttendees = new JCheckBox("export attendees of vevent");
        txtEMailRessourceAttribute = new JTextArea(Export2iCalPlugin.DEFAULT_attendee_resource_attribute);
        RaplaGUIComponent.addCopyPaste(txtEMailRessourceAttribute, getI18n(), getRaplaLocale(), ioInterface, getLogger());
        txtEMailRessourceAttribute.setToolTipText("Define the key of the attribute containing the email address");
        @SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(new Object [] {
                "ACCEPTED", "TENTATIVE"
        });
		cbDefaultParticipationsStatusRessourceAttribute = jComboBox;
        cbDefaultParticipationsStatusRessourceAttribute.setSelectedItem(Export2iCalPlugin.DEFAULT_attendee_participation_status);
        cbDefaultParticipationsStatusRessourceAttribute.setToolTipText("Define the default value for participation status");


		spiDaysBefore = new JSpinner(new SpinnerNumberModel(Export2iCalPlugin.DEFAULT_daysBefore, 0, null, 1));
		spiDaysAfter = new JSpinner(new SpinnerNumberModel(Export2iCalPlugin.DEFAULT_daysAfter, 0, null, 1));
		optGlobalInterval = new JRadioButton("global interval setting");
		optUserInterval = new JRadioButton("user interval settings");
		lblLastModifiedInterval = new JLabel("interval for delivery in days");
	
		//String[] availableIDs = net.fortuna.ical4j.model.TimeZone.getAvailableIDs();
		
	
		ButtonGroup group = new ButtonGroup();
		group.add(optGlobalInterval);
		group.add(optUserInterval);
		JPanel panel = super.createPanel();
		JPanel content = new JPanel();
		double[][] sizes = new double[][] {
				{       5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5 },
				{       TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
						TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5  } };
		TableLayout tableLayout = new TableLayout(sizes);
		content.setLayout(tableLayout);
		content.add(optGlobalInterval, "1,4");
		content.add(optUserInterval, "3,4");
		content.add(new JLabel("previous days:"), "1,6");
		content.add(spiDaysBefore, "3,6");
		content.add(new JLabel("subsequent days:"), "1,8");
		content.add(spiDaysAfter, "3,8");
		content.add(chkUseLastModifiedIntervall, "1,12");
		content.add(lblLastModifiedInterval, "1,14");
		content.add(spiLastModifiedInterval, "3,14");

        content.add(chkExportAttendees, "1,16");
        content.add(new JLabel("attribute key in person-type:"), "1,18");
        content.add(txtEMailRessourceAttribute, "3,18");
        content.add(new JLabel("participation status:"), "1,20");
        content.add(cbDefaultParticipationsStatusRessourceAttribute, "3,20");

        panel.add(content, BorderLayout.CENTER);
		optUserInterval.addActionListener(this);
		optGlobalInterval.addActionListener(this);
		chkUseLastModifiedIntervall.addActionListener(this);
        chkExportAttendees.addActionListener(this);
	
		return panel;
	}

	@Override
	public void setPreferences(Preferences preferences) 
	{
	    super.setPreferences(preferences);
	}
	
    @Override
    public void commit() throws RaplaException {
        writePluginConfig(false);
        TypedComponentRole<RaplaConfiguration> configEntry = Export2iCalPlugin.ICAL_CONFIG;
        RaplaConfiguration newConfig = new RaplaConfiguration("config" );
        addChildren( newConfig );
        preferences.putEntry( configEntry,newConfig);
    }
    
	protected void addChildren(DefaultConfiguration newConfig) {
		newConfig.getMutableChild(Export2iCalPlugin.DAYS_BEFORE, true).setValue(Integer.parseInt(spiDaysBefore.getValue().toString()));
		newConfig.getMutableChild(Export2iCalPlugin.DAYS_AFTER, true).setValue(Integer.parseInt(spiDaysAfter.getValue().toString()));
		newConfig.getMutableChild(Export2iCalPlugin.GLOBAL_INTERVAL, true).setValue(optGlobalInterval.isSelected());

		String lastModIntervall = chkUseLastModifiedIntervall.isSelected() ? new String("-1") : spiLastModifiedInterval.getValue().toString();
		newConfig.getMutableChild(Export2iCalPlugin.LAST_MODIFIED_INTERVALL, true).setValue(lastModIntervall);

		newConfig.getMutableChild(Export2iCalPlugin.ENABLED_STRING, true).setValue(activate.isSelected());
        newConfig.getMutableChild(Export2iCalPlugin.EXPORT_ATTENDEES, true).setValue(chkExportAttendees.isSelected());
        newConfig.getMutableChild(Export2iCalPlugin.EXPORT_ATTENDEES_EMAIL_ATTRIBUTE, true).setValue(txtEMailRessourceAttribute.getText());
        newConfig.getMutableChild(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS, true).setValue(cbDefaultParticipationsStatusRessourceAttribute.getSelectedItem().toString());

	}
	
	@Override
	protected Configuration getConfig() throws RaplaException {
	    Configuration config = preferences.getEntry( Export2iCalPlugin.ICAL_CONFIG, null);
	    if ( config == null )
	    {
	        config =  configService.getConfig();
        } 
	    return config;
	}

	protected void readConfig(Configuration config) {
	    
		int daysBefore = config.getChild(Export2iCalPlugin.DAYS_BEFORE).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysBefore);
		int daysAfter = config.getChild(Export2iCalPlugin.DAYS_AFTER).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysAfter);
		int lastModifiedIntervall = config.getChild(Export2iCalPlugin.LAST_MODIFIED_INTERVALL).getValueAsInteger(Export2iCalPlugin.DEFAULT_lastModifiedIntervall);

		boolean global_interval = config.getChild(Export2iCalPlugin.GLOBAL_INTERVAL).getValueAsBoolean(Export2iCalPlugin.DEFAULT_globalIntervall);

        boolean exportAttendees = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES).getValueAsBoolean(Export2iCalPlugin.DEFAULT_exportAttendees);
		String exportAttendeeDefaultEmailAttribute = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_EMAIL_ATTRIBUTE).getValue(Export2iCalPlugin.DEFAULT_attendee_resource_attribute);
		String exportAttendeeParticipationStatus = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS).getValue(Export2iCalPlugin.DEFAULT_attendee_participation_status);
		
		if (lastModifiedIntervall == -1) {
			spiLastModifiedInterval.setEnabled(false);
			lblLastModifiedInterval.setEnabled(false);
			chkUseLastModifiedIntervall.setSelected(true);
		} else {
			spiLastModifiedInterval.setValue(new Integer(lastModifiedIntervall));
			lblLastModifiedInterval.setEnabled(true);
			chkUseLastModifiedIntervall.setSelected(false);
		}

		optGlobalInterval.setSelected(global_interval);
		optUserInterval.setSelected(!global_interval);

		spiDaysBefore.setValue(new Integer(daysBefore));
		spiDaysAfter.setValue(new Integer(daysAfter));

		

        chkExportAttendees.setSelected(exportAttendees);

        txtEMailRessourceAttribute.setText(exportAttendeeDefaultEmailAttribute);
        txtEMailRessourceAttribute.setEnabled(chkExportAttendees.isSelected());

        cbDefaultParticipationsStatusRessourceAttribute.setSelectedItem(exportAttendeeParticipationStatus);
        cbDefaultParticipationsStatusRessourceAttribute.setEnabled(chkExportAttendees.isSelected());
		//this.setTextFieldInput();
	}

	public String getName(Locale locale) {
		return "Export2iCal";
	}

	/*
	 //This is now not needed anymore
	private void setTextFieldInput() {
		this.spiDaysBefore.setEnabled(optGlobalInterval.isSelected());
		this.spiDaysAfter.setEnabled(optGlobalInterval.isSelected());
	}*/



	public void actionPerformed(ActionEvent e) {
		//this.setTextFieldInput();
		if (e.getSource() == chkUseLastModifiedIntervall) {
			spiLastModifiedInterval.setEnabled(!chkUseLastModifiedIntervall.isSelected());
			lblLastModifiedInterval.setEnabled(!chkUseLastModifiedIntervall.isSelected());
		}

        if (e.getSource() == chkExportAttendees) {
            txtEMailRessourceAttribute.setEnabled(chkExportAttendees.isSelected());
            cbDefaultParticipationsStatusRessourceAttribute.setEnabled(chkExportAttendees.isSelected());
        }
	}
}
