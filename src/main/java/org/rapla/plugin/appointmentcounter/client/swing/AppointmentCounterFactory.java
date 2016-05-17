package org.rapla.plugin.appointmentcounter.client.swing;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComponent;
import javax.swing.JLabel;

import org.rapla.RaplaResources;
import org.rapla.client.AppointmentListener;
import org.rapla.client.ReservationEdit;
import org.rapla.client.extensionpoints.AppointmentStatusFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.RaplaWidget;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides = AppointmentStatusFactory.class, id="appointmentcounter")
@Singleton
public class AppointmentCounterFactory implements AppointmentStatusFactory 
{
    private final ClientFacade facade;
    private final RaplaResources i18n;
    private final RaplaLocale raplaLocale;
    private final Logger logger;

	@Inject
	public AppointmentCounterFactory(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
	{
	    super();
	    this.facade = facade;
	    this.i18n = i18n;
	    this.raplaLocale = raplaLocale;
	    this.logger = logger;
	}
	
	public RaplaWidget createStatus(ReservationEdit reservationEdit) throws RaplaException
	{
		return new AppointmentCounter(facade, i18n, raplaLocale, logger, reservationEdit);
	}
	
    class AppointmentCounter extends RaplaGUIComponent implements RaplaWidget
	{
		JLabel statusBar = new JLabel();
		
		   
		ReservationEdit reservationEdit;
		public AppointmentCounter(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, final ReservationEdit reservationEdit) {
			super(facade, i18n, raplaLocale, logger);

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

