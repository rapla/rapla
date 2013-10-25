package org.rapla.plugin.eventtimecalculator.client;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.layout.TableLayout;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;

/**
 * ****************************************************************************
 * This is the admin-option panel.
 *
 * @author Tobias Bertram
 */
public class EventTimeCalculatorOption extends RaplaGUIComponent {

    private RaplaNumber intervalNumber;
    private RaplaNumber breakNumber;
    //private RaplaNumber lunchbreakNumber;
    private RaplaNumber timeUnit;
    private JTextField timeFormat;
    private JCheckBox chkAllowUserPrefs;
    boolean adminOptions;

    public EventTimeCalculatorOption(RaplaContext sm, boolean adminOptions)  {
        super(sm);
        this.adminOptions = adminOptions;
        setChildBundleName(EventTimeCalculatorPlugin.RESOURCE_FILE);
    }

    /**
     * creates the panel shown in the admin option dialog.
     */
    protected JPanel createPanel() {
        
        JPanel content = new JPanel();
        double[][] sizes = new double[][]{
                {5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5},
                        {TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                                TableLayout.PREFERRED, 5,
                                TableLayout.PREFERRED, 5
                }};
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
        content.add(new JLabel(getString("time_till_break") + ":"), "1,0");
        content.add(new JLabel(getString("break_duration") + ":"), "1,2");
      //  content.add(new JLabel(i18n.getString("lunch_break_duration") + ":"), "1,4");
        content.add(new JLabel(getString("time_unit") + ":"), "1,6");
        content.add(new JLabel(getString("time_format") + ":"), "1,8");

        intervalNumber = new RaplaNumber(EventTimeCalculatorPlugin.DEFAULT_intervalNumber, 0, null, false);
        content.add(intervalNumber, "3,0");
        content.add(new JLabel(getString("minutes")), "5,0");

        breakNumber = new RaplaNumber(EventTimeCalculatorPlugin.DEFAULT_breakNumber, 0, null, false);
        content.add(breakNumber, "3,2");
        content.add(new JLabel(getString("minutes")), "5,2");

//        lunchbreakNumber = new RaplaNumber(EventTimeCalculatorPlugin.DEFAULT_lunchbreakNumber, new Integer(1), null, false);
//        content.add(lunchbreakNumber, "3,4");
//        content.add(new JLabel(i18n.getString("minutes")), "5,4");

        timeUnit = new RaplaNumber(EventTimeCalculatorPlugin.DEFAULT_timeUnit, 1, null, false);
        content.add(timeUnit, "3,6");
        content.add(new JLabel(getString("minutes")), "5,6");

        timeFormat = new JTextField();
        content.add(timeFormat, "3,8");
        if ( adminOptions)
        {
        	chkAllowUserPrefs = new JCheckBox();
        	content.add(chkAllowUserPrefs, "3,10");
        	content.add(new JLabel(getString("allow_user_prefs")), "1,10");
        }
        return content;
    }

    /**
     * adds new configuration to the children to overwrite the default configuration.
     */
    protected void addChildren(DefaultConfiguration newConfig) {
        newConfig.getMutableChild(EventTimeCalculatorPlugin.INTERVAL_NUMBER, true).setValue(intervalNumber.getNumber().intValue());
        newConfig.getMutableChild(EventTimeCalculatorPlugin.BREAK_NUMBER, true).setValue(breakNumber.getNumber().intValue());
//            newConfig.getMutableChild(EventTimeCalculatorPlugin.LUNCHBREAK_NUMBER, true).setValue(lunchbreakNumber.getNumber().intValue());
        newConfig.getMutableChild(EventTimeCalculatorPlugin.TIME_UNIT, true).setValue(timeUnit.getNumber().intValue());
        newConfig.getMutableChild(EventTimeCalculatorPlugin.TIME_FORMAT, true).setValue(timeFormat.getText());
        if ( adminOptions)
        {
        	newConfig.getMutableChild(EventTimeCalculatorPlugin.USER_PREFS, true).setValue(chkAllowUserPrefs.isSelected());
        }
    }

    /**
     * reads children out of the configuration and shows them in the admin option panel.
     */
    protected void readConfig(Configuration config) {
        int intervalNumberInt = config.getChild(EventTimeCalculatorPlugin.INTERVAL_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_intervalNumber);
        intervalNumber.setNumber(intervalNumberInt);
        int breakNumberInt = config.getChild(EventTimeCalculatorPlugin.BREAK_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_breakNumber);
        breakNumber.setNumber(breakNumberInt);
//        int lunchbreakNumberInt = config.getChild(EventTimeCalculatorPlugin.LUNCHBREAK_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_lunchbreakNumber);
//        lunchbreakNumber.setNumber(new Integer(lunchbreakNumberInt));
        int timeUnitInt = config.getChild(EventTimeCalculatorPlugin.TIME_UNIT).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_timeUnit);
        timeUnit.setNumber(timeUnitInt);
        String timeFormatString = config.getChild(EventTimeCalculatorPlugin.TIME_FORMAT).getValue(EventTimeCalculatorPlugin.DEFAULT_timeFormat);
        timeFormat.setText(timeFormatString);
        if ( adminOptions)
        {
        	boolean allowUserPrefs = config.getChild(EventTimeCalculatorPlugin.USER_PREFS).getValueAsBoolean(EventTimeCalculatorPlugin.DEFAULT_userPrefs);
        	chkAllowUserPrefs.setSelected(allowUserPrefs);
        }
    }

    

  
}

