package org.rapla.plugin.appointmentcounter;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AppointmentListener;
import org.rapla.gui.AppointmentStatusFactory;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.toolkit.RaplaWidget;

public class AppointmentCounterFactory implements AppointmentStatusFactory 
{
	public RaplaWidget createStatus(RaplaContext context, ReservationEdit reservationEdit) throws RaplaException
	{
		return new AppointmentCounter(context, reservationEdit);
	}
	
	class AppointmentCounter extends RaplaGUIComponent implements RaplaWidget
	{
		JLabel statusBar = new JLabel();
		
		   
		ReservationEdit reservationEdit;
		public AppointmentCounter(final RaplaContext context, final ReservationEdit reservationEdit) {
			super(context);

			Font font = statusBar.getFont().deriveFont( (float)9.0);
			statusBar.setFont( font ); 
		    
			this.reservationEdit = reservationEdit;
			reservationEdit.addAppointmentListener( new AppointmentListener() {
				
				public void appointmentRemoved(Collection<Appointment> appointment) {
					updateStatus();	
				}
				
				
				public void appointmentChanged(Collection<Appointment> appointment) {
					updateStatus();
				}
				
				public void appointmentAdded(Collection<Appointment> appointment) {
					updateStatus();
				}

				public void appointmentSelected(Collection<Appointment> appointment) {
				    updateStatus();	
				}
			});
			updateStatus();
			
		}
		
		 private void updateStatus() {
		        Reservation event = reservationEdit.getReservation();
		        if ( event == null)
		        {
		        	return;
		        }
		        Appointment[] appointments = event.getAppointments();
		        int count = 0;
		        for (int i = 0; i<appointments.length; i++) {
		            Appointment appointment = appointments[i];
		            Repeating repeating = appointment.getRepeating();
		            if ( repeating == null ) {
		                count ++;
		                continue;
		            }
		            if ( repeating.getEnd() == null ){ // Repeats foreever ?
		                count = -1;
		                break;
		            }
		            List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
		            appointment.createBlocks( appointment.getStart(), DateTools.fillDate(repeating.getEnd()), blocks);
		            count += blocks.size();
		        }
		        String status = "";
		        if (count >= 0)
		            status = getString("total_occurances")+ ": " + count;
		        else
		        	status = getString("total_occurances")+ ": ? ";
		        // uncomment for selection change test
		        // status +=  "Selected Appointments " + reservationEdit.getSelectedAppointments();
		        statusBar.setText( status );
		    }


		 
		public JComponent getComponent() {
			return statusBar;
		}
		
	}
}

