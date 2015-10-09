package org.rapla.plugin.export2ical;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.rapla.client.extensionpoints.ExportMenuExtension;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.inject.Extension;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;

@Extension(provides = ExportMenuExtension.class, id = Export2iCalPlugin.PLUGIN_ID)
public class Export2iCalMenu extends RaplaGUIComponent implements ExportMenuExtension, ActionListener{

	String id = "export_file_text";
	JMenuItem item;
	ICalExport exportService;
	final Export2iCalResources i18nIcal;

	@Inject
	public Export2iCalMenu(RaplaContext sm,ICalExport exportService, Export2iCalResources i18nIcal){
		super(sm);
		this.exportService = exportService;
		this.i18nIcal = i18nIcal;
		item = new JMenuItem(i18nIcal.getString(id));
		item.setIcon(getIcon("icon.export"));
		item.addActionListener(this);
	}
	
	public String getId() {
		return id;
	}

	public JMenuItem getMenuElement() {
		return item;
	}
	
	public void actionPerformed(ActionEvent evt) {
		getCalendarOptions();
		try {
			CalendarModel raplaCal = getService(CalendarModel.class);
		    Reservation[] reservations = raplaCal.getReservations();
		    Allocatable[] allocatables = raplaCal.getSelectedAllocatables();
		    List<Appointment> appointments= RaplaBuilder.getAppointments( reservations, allocatables);
		    String[] appointmentIds = new String[appointments.size()];
		    for ( int i=0;i<appointmentIds.length;i++)
		    {
		    	appointmentIds[i] =  appointments.get(i).getId();
		    }
		    String result = exportService.export(appointmentIds);
		    if ( result.trim().length() == 0)
		    {
				String no_dates_text = i18nIcal.getString("no_dates_text");
				JOptionPane.showMessageDialog(null, no_dates_text, "Export2iCal", JOptionPane.INFORMATION_MESSAGE);
		    }
		    else
		    {
		        String nonEmptyTitle = raplaCal.getNonEmptyTitle();
		        if ( nonEmptyTitle.length() == 0)
		        {
		            nonEmptyTitle = "rapla_calendar";
		        }
                String name =nonEmptyTitle +".ics";
		        export(result, name);
		    }
		} catch (Exception ex) {
			showException(ex, getMainComponent());
		}
	}
	private void export(String result, String name) throws RaplaException {
		final byte[] bytes = result.getBytes();
        saveFile(bytes, name , new String[] {"ics","ical"});
	}

	public void saveFile(byte[] content, String filename, String[] extensions) throws RaplaException {
		final Frame frame = (Frame) SwingUtilities.getRoot(getMainComponent());
		IOInterface io = getService(IOInterface.class);
		try {
			io.saveFile(frame, null, extensions, filename, content);
		} catch (IOException e) {
			throw new RaplaException("Can't export file!", e);
		}
	}


	
}
