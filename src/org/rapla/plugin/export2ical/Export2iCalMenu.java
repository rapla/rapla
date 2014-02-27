package org.rapla.plugin.export2ical;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;

public class Export2iCalMenu extends RaplaGUIComponent implements IdentifiableMenuEntry, ActionListener{

	String id = "export_file_text";
	JMenuItem item;
	ICalExport exportService;
	public Export2iCalMenu(RaplaContext sm,ICalExport exportService){
		super(sm);
		this.exportService = exportService;
		setChildBundleName(Export2iCalPlugin.RESOURCE_FILE);
		item = new JMenuItem(getString(id));
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
		        JOptionPane.showMessageDialog(null, getString("no_dates_text"), "Export2iCal", JOptionPane.INFORMATION_MESSAGE);
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
