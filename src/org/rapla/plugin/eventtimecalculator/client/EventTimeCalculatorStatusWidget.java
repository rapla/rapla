package org.rapla.plugin.eventtimecalculator.client;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AppointmentListener;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.toolkit.RaplaWidget;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorFactory;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;
import org.rapla.plugin.eventtimecalculator.EventTimeModel;

/**
 * @author Tobias Bertram
 *         Class EventTimeCalculator provides the service to show the actual duration of all appointments of a reservation.
 */
public class EventTimeCalculatorStatusWidget extends RaplaGUIComponent implements RaplaWidget {
    JPanel content = new JPanel();
    JLabel totalDurationLabel = new JLabel();
    I18nBundle i18n;
    ReservationEdit reservationEdit;
    EventTimeCalculatorFactory factory;

    /**
     * creates the panel for the GUI in window "reservation".
     */
    public EventTimeCalculatorStatusWidget(final RaplaContext context, final ReservationEdit reservationEdit) throws RaplaException {
        super(context);
        factory = context.lookup(EventTimeCalculatorFactory.class);
        //this.config = config;
        i18n = context.lookup(EventTimeCalculatorPlugin.RESOURCE_FILE);
        setChildBundleName(EventTimeCalculatorPlugin.RESOURCE_FILE);

        double[][] sizes = new double[][]{
                {5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5},
                {TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5
                }};
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);

        Font font1 = totalDurationLabel.getFont().deriveFont((float) 9.0);
        totalDurationLabel.setFont(font1);

        content.add(totalDurationLabel, "1,2");
        this.reservationEdit = reservationEdit;

        /**
         * updates Panel if an appointment is removed, changed, added.
         */
        reservationEdit.addAppointmentListener(new AppointmentListener() {
           
            public void appointmentSelected(Collection<Appointment> appointment) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void appointmentRemoved(Collection<Appointment> appointment) {
                updateStatus();
            }

            public void appointmentChanged(Collection<Appointment> appointment) {
                updateStatus();
            }

            public void appointmentAdded(Collection<Appointment> appointment) {
                updateStatus();
            }
        });
        updateStatus();
    }

    /**
     * provides the necessary parameters to use the class TimeCalculator.
     * also provides some logic needed for the calculation of the actual duration of all appointments in the shown reservation.
     */
    private void updateStatus()  {
        
        Reservation event = reservationEdit.getReservation();
        if (event == null)
        {
            return;
        }


        Appointment[] appointments = event.getAppointments();
        boolean noEnd = false;

        long totalDuration = 0;
    	EventTimeModel eventTimeModel = factory.getEventTimeModel();
        
        for (Appointment appointment : appointments) { // goes through all appointments of the reservation
            if (appointment.getRepeating() != null && appointment.getRepeating().getEnd() == null) { // appoinment repeats forever?
                noEnd = true;
                break;
            }
            java.util.List<AppointmentBlock> splits = new ArrayList<AppointmentBlock>(); // split appointment block
            appointment.createBlocks(appointment.getStart(),
                    DateTools.fillDate(appointment.getMaxEnd()), splits);
            for (AppointmentBlock block : splits) { // goes through the block
                long duration = DateTools.countMinutes(block.getStart(), block.getEnd());

                // lunch break flag: here the lunchBreakActivated-Flag should be taken out of the preferences and given to the calculateActualDuration-method

//                final long TIME_TILL_BREAK_DURATION = config.getChild(EventTimeCalculatorPlugin.INTERVAL_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_timeUnit);
//                final long BREAK_DURATION = config.getChild(EventTimeCalculatorPlugin.BREAK_NUMBER).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_breakNumber);
                long actualDuration = eventTimeModel.calcDuration(duration); // EventTimeCalculatorFactory.calculateActualDuration(duration, TIME_TILL_BREAK_DURATION, BREAK_DURATION);
                totalDuration += actualDuration;
            }
        }

       // String format = EventTimeCalculatorFactory.format(config, totalDuration);
        totalDurationLabel.setText(getString("total_duration") + ": " + eventTimeModel.format(totalDuration));
        totalDurationLabel.setVisible(!noEnd);
    }

  /*  public String formatDuration(Configuration config, long totalDuration) {
        final String format = config.getChild(EventTimeCalculatorPlugin.TIME_FORMAT).getValue(EventTimeCalculatorPlugin.DEFAULT_timeFormat);
        final int timeUnit = config.getChild(EventTimeCalculatorPlugin.TIME_UNIT).getValueAsInteger(EventTimeCalculatorPlugin.DEFAULT_timeUnit);
        return  MessageFormat.format(format, totalDuration / timeUnit, totalDuration % timeUnit);
    }*/

    /**
     * returns the panel shown in the window "reservation"
     */
    public JComponent getComponent() {
        return content;
    }


}
