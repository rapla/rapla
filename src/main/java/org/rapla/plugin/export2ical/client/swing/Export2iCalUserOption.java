package org.rapla.plugin.export2ical.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.Export2iCalResources;
import org.rapla.plugin.export2ical.ICalConfigService;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

/***
 * This is the user-option panel
 * @author Twardon
 *
 */
@Extension(provides = UserOptionPanel.class, id= Export2iCalPlugin.PLUGIN_ID)
public class Export2iCalUserOption extends RaplaGUIComponent implements UserOptionPanel, ActionListener {
	
	private Preferences preferences;
	private final JPanel panel = new JPanel();
	
	private JSpinner spiDaysBefore;
	private JSpinner spiDaysAfter;
	
	private JCheckBox chkUseUserdefinedIntervall;
	
	public boolean addButtons = true;
	private boolean global_interval;
	
	private int user_days_before;
	private int user_days_after;
	private int global_days_before;
	private int global_days_after;
	
	private boolean userdefined;
    private JCheckBox chkExportAttendees;
    private JComboBox cbDefaultParticipationsStatusRessourceAttribute;
    private boolean user_export_attendees;
    private String user_export_attendees_participants_status;

    ICalConfigService configService;
	final Export2iCalResources i18nIcal;

	@Inject
    public Export2iCalUserOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,ICalConfigService configService, Export2iCalResources i18nIcal)
	{
		super(facade, i18n, raplaLocale, logger);
		this.configService = configService;
		this.i18nIcal = i18nIcal;
	}
	
	@Override
	public boolean isEnabled()
	{
	    RaplaConfiguration config;
        try
        {
            config = getFacade().getSystemPreferences().getEntry(Export2iCalPlugin.ICAL_CONFIG, new RaplaConfiguration());
        }
        catch (RaplaException e)
        {
            return false;
        }
        final boolean enabled = config.getAttributeAsBoolean("enabled", Export2iCalPlugin.ENABLE_BY_DEFAULT);
        return enabled;
	}

	public JComponent getComponent() {
		return panel;
	}

	public String getName(Locale locale) {
		return i18nIcal.getString("ical_export_user_settings");
	}

	public void createList()  {

		panel.removeAll();
        chkExportAttendees = new JCheckBox(i18nIcal.getString("export_attendees_of_vevent"));
        chkExportAttendees.addActionListener(this);
        @SuppressWarnings("unchecked")
		JComboBox jComboBox = new JComboBox(new String [] {
                "ACCEPTED",
                "TENTATIVE"
        });
		cbDefaultParticipationsStatusRessourceAttribute = jComboBox;
        cbDefaultParticipationsStatusRessourceAttribute.setSelectedItem(Export2iCalPlugin.DEFAULT_attendee_participation_status);
        cbDefaultParticipationsStatusRessourceAttribute.setToolTipText("Define the default value for participation status");

		double[][] sizes = new double[][] { { 5, TableLayout.FILL, 5,TableLayout.FILL, 5  },
				{ TableLayout.PREFERRED, 5,
                  TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.FILL, 5,
                        TableLayout.PREFERRED } };

		TableLayout tableLayout = new TableLayout(sizes);
		panel.setLayout(tableLayout);

		JPanel interval = new JPanel();
		interval.add(new JLabel(i18nIcal.getString("previous_days_text")));
		spiDaysBefore = new JSpinner(new SpinnerNumberModel(30, 0, 100, 1));
		interval.add(spiDaysBefore);
		interval.add(new JLabel(i18nIcal.getString("subsequent_days_text")));
		spiDaysAfter = new JSpinner(new SpinnerNumberModel(30, 0, 100, 1));
		interval.add(spiDaysAfter);
		chkUseUserdefinedIntervall = new JCheckBox(i18nIcal.getString("use_user_interval_setting_text"));
		chkUseUserdefinedIntervall.setSelected(userdefined);
		interval.add(chkUseUserdefinedIntervall);
		int before = global_interval ? global_days_before : user_days_before;
		spiDaysBefore.setValue(Integer.valueOf(before));
		
		int after = global_interval ? global_days_after : user_days_after;
		spiDaysAfter.setValue(Integer.valueOf(after));
		
		if (addButtons) {
			panel.add(new JLabel(i18nIcal.getString("user_interval_setting_text")), "1,0");
			panel.add(chkUseUserdefinedIntervall,"1,2");
			panel.add(interval, "1,4");
		}

        // set values
        chkExportAttendees.setSelected(user_export_attendees);
        cbDefaultParticipationsStatusRessourceAttribute.setSelectedItem(user_export_attendees_participants_status);
        cbDefaultParticipationsStatusRessourceAttribute.setEnabled(user_export_attendees);

        panel.add(chkExportAttendees, "1,6");
        panel.add(new JLabel(i18nIcal.getString("participation_status")), "1,8");
        panel.add(cbDefaultParticipationsStatusRessourceAttribute, "3,8");
	
		chkUseUserdefinedIntervall.setEnabled(!global_interval);
		spiDaysAfter.setEnabled(userdefined);
		spiDaysBefore.setEnabled(userdefined);

		chkUseUserdefinedIntervall.addActionListener(this);
	}

	public void show() throws RaplaException {
	    Configuration config = configService.getUserDefaultConfig();
	    
		global_days_before = config.getChild(Export2iCalPlugin.DAYS_BEFORE).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysBefore);
		global_days_after = config.getChild(Export2iCalPlugin.DAYS_AFTER).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysAfter);
		
		userdefined = (preferences.hasEntry(Export2iCalPlugin.PREF_BEFORE_DAYS) || preferences.hasEntry(Export2iCalPlugin.PREF_AFTER_DAYS));
		
		user_days_before = preferences.getEntryAsInteger(Export2iCalPlugin.PREF_BEFORE_DAYS, global_days_before);
		user_days_after = preferences.getEntryAsInteger(Export2iCalPlugin.PREF_AFTER_DAYS, global_days_after);
		
		global_interval = config.getChild(Export2iCalPlugin.GLOBAL_INTERVAL).getValueAsBoolean(Export2iCalPlugin.DEFAULT_globalIntervall);
        boolean global_export_attendees = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES).getValueAsBoolean(Export2iCalPlugin.DEFAULT_exportAttendees);
        String global_export_attendees_participants_status = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS).getValue(Export2iCalPlugin.DEFAULT_attendee_participation_status);

        user_export_attendees = preferences.getEntryAsBoolean(Export2iCalPlugin.EXPORT_ATTENDEES_PREFERENCE, global_export_attendees);
        user_export_attendees_participants_status = preferences.getEntryAsString(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE, global_export_attendees_participants_status);

        createList();
	}

	public void setPreferences(Preferences preferences) {
		this.preferences = preferences;
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==chkUseUserdefinedIntervall){
			spiDaysBefore.setEnabled(chkUseUserdefinedIntervall.isSelected());
			spiDaysAfter.setEnabled(chkUseUserdefinedIntervall.isSelected());
		}
        if (e.getSource() == chkExportAttendees) {
            cbDefaultParticipationsStatusRessourceAttribute.setEnabled(chkExportAttendees.isSelected());
        }
	}

	public void commit() {
		
		//saving an null object will delete the setting
		if(!chkUseUserdefinedIntervall.isSelected()){
			preferences.putEntry(Export2iCalPlugin.PREF_BEFORE_DAYS, null);
			preferences.putEntry(Export2iCalPlugin.PREF_AFTER_DAYS, null);
		}else{
			preferences.putEntry(Export2iCalPlugin.PREF_BEFORE_DAYS, Integer.valueOf(this.spiDaysBefore.getValue().toString()));
			preferences.putEntry(Export2iCalPlugin.PREF_AFTER_DAYS, Integer.valueOf(this.spiDaysAfter.getValue().toString()));
		}

        preferences.putEntry(Export2iCalPlugin.EXPORT_ATTENDEES_PREFERENCE, chkExportAttendees.isSelected());
        preferences.putEntry(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE, cbDefaultParticipationsStatusRessourceAttribute.getSelectedItem().toString());
	}

}
