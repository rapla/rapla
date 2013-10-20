package org.rapla.plugin.export2ical;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;

public class Export2iCalMenu extends RaplaGUIComponent implements IdentifiableMenuEntry, ActionListener{

	String id = "export_file_text";
	JMenuItem item;

	public Export2iCalMenu(RaplaContext sm){
		super(sm);
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
		    StringBuffer buf = new StringBuffer();
		    for (Reservation reservation:raplaCal.getReservations())
		    {
		        boolean first = buf.length() == 0;
		        if ( !first)
		            buf.append(",");
		        final String idString = ((RefEntity<?>) reservation).getId().toString().split("_")[1];
                buf.append( idString );
		    }
		    String ids = buf.toString();
		    String result = getWebservice( ICalExport.class).export(ids);
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
