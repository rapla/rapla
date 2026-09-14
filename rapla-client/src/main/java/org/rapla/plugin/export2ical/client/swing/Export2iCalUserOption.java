package org.rapla.plugin.export2ical.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.Export2iCalResources;
import org.rapla.plugin.export2ical.ICalConfigService;
import org.rapla.plugin.export2ical.UserICalSettings;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
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
@Service
@Scope("prototype")

public class Export2iCalUserOption extends RaplaGUIComponent implements UserOptionPanel, ActionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Export2iCalUserOption.class);
	
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

	@Autowired
    public Export2iCalUserOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale,ICalConfigService configService, Export2iCalResources i18nIcal)
	{
		super(facade, i18n, raplaLocale);
		this.configService = configService;
		this.i18nIcal = i18nIcal;
	}
	
	/** Cached enabled flag — isEnabled() is called repeatedly during user-options rendering. */
	private volatile Boolean cachedEnabled;

	@Override
	public boolean isEnabled()
	{
	    Boolean cached = cachedEnabled;
	    if (cached != null) return cached;
        try
        {
            // Previously read .server.-named ICAL_CONFIG from system prefs.
            // That key is now stripped from the bulk bootstrap for admins
            // too (PRD 026 §5 security fix); fetch via the dedicated endpoint.
            Configuration config = configService.getUserDefaultConfig();
            boolean enabled = config != null && config.getAttributeAsBoolean("enabled", Export2iCalPlugin.ENABLE_BY_DEFAULT);
            cachedEnabled = enabled;
            return enabled;
        }
        catch (RaplaException e)
        {
            LOGGER.warn("Failed to load iCal config via /ical/config/default; falling back to default", e);
            return Export2iCalPlugin.ENABLE_BY_DEFAULT;
        }
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
	    // System defaults (any-user) + per-user overrides — both via REST
	    // instead of the bulk /storage/resources preference cache.
	    LOGGER.info("Export2iCalUserOption.show(): fetching /ical/config/{default,user} via REST");
	    Configuration config = configService.getUserDefaultConfig();

		global_days_before = config.getChild(Export2iCalPlugin.DAYS_BEFORE).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysBefore);
		global_days_after = config.getChild(Export2iCalPlugin.DAYS_AFTER).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysAfter);
		global_interval = config.getChild(Export2iCalPlugin.GLOBAL_INTERVAL).getValueAsBoolean(Export2iCalPlugin.DEFAULT_globalIntervall);
        boolean global_export_attendees = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES).getValueAsBoolean(Export2iCalPlugin.DEFAULT_exportAttendees);
        String global_export_attendees_participants_status = config.getChild(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS).getValue(Export2iCalPlugin.DEFAULT_attendee_participation_status);

        UserICalSettings userSettings;
        try
        {
            userSettings = configService.getUserSettings();
        }
        catch (Exception e)
        {
            LOGGER.warn("GET /ical/config/user failed, falling back to local cache: {}", e.getMessage());
            userSettings = new UserICalSettings(
                    preferences.hasEntry(Export2iCalPlugin.PREF_BEFORE_DAYS)
                            ? preferences.getEntryAsInteger(Export2iCalPlugin.PREF_BEFORE_DAYS, 0) : null,
                    preferences.hasEntry(Export2iCalPlugin.PREF_AFTER_DAYS)
                            ? preferences.getEntryAsInteger(Export2iCalPlugin.PREF_AFTER_DAYS, 0) : null,
                    preferences.hasEntry(Export2iCalPlugin.EXPORT_ATTENDEES_PREFERENCE)
                            ? preferences.getEntryAsBoolean(Export2iCalPlugin.EXPORT_ATTENDEES_PREFERENCE, false) : null,
                    preferences.hasEntry(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE)
                            ? preferences.getEntryAsString(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE, null) : null
            );
        }

        userdefined = (userSettings.daysBefore() != null || userSettings.daysAfter() != null);
        user_days_before = userSettings.daysBefore() != null ? userSettings.daysBefore() : global_days_before;
        user_days_after = userSettings.daysAfter() != null ? userSettings.daysAfter() : global_days_after;
        user_export_attendees = userSettings.exportAttendees() != null ? userSettings.exportAttendees() : global_export_attendees;
        user_export_attendees_participants_status = userSettings.participationStatus() != null
                ? userSettings.participationStatus() : global_export_attendees_participants_status;

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
