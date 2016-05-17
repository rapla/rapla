package org.rapla.plugin.export2ical.client.swing;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.ExportMenuExtension;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Appointment;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.Export2iCalResources;
import org.rapla.plugin.export2ical.ICalExport;
import org.rapla.scheduler.Promise;

@Extension(provides = ExportMenuExtension.class, id = Export2iCalPlugin.PLUGIN_ID)
public class Export2iCalMenu extends RaplaGUIComponent implements ExportMenuExtension, ActionListener{

	String id = "export_file_text";
	JMenuItem item;
	ICalExport exportService;
	final Export2iCalResources i18nIcal;
    private final CalendarModel calendarModel;
    private final IOInterface ioInterface;
    private final DialogUiFactoryInterface dialogUiFactory;

	@Inject
	public Export2iCalMenu(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,ICalExport exportService, Export2iCalResources i18nIcal, CalendarModel calendarModel, IOInterface ioInterface, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory){
		super(facade, i18n, raplaLocale, logger);
		this.exportService = exportService;
		this.i18nIcal = i18nIcal;
        this.calendarModel = calendarModel;
        this.ioInterface = ioInterface;
        this.dialogUiFactory = dialogUiFactory;
		item = new JMenuItem(i18nIcal.getString(id));
		item.setIcon(raplaImages.getIconFromKey("icon.export"));
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
		final TimeInterval interval = new TimeInterval(calendarModel.getStartDate(), calendarModel.getEndDate());
		Promise<Collection<Appointment>> appointmentsPromise = calendarModel.queryAppointments(interval);
		appointmentsPromise.thenAccept((appointments) -> {

		    Set<String> appointmentIds = new LinkedHashSet<String>();
		    for ( Appointment app:appointments)
		    {
		    	appointmentIds.add(app.getId());
		    }
		    String result = exportService.export(appointmentIds);
		    if ( result.trim().length() == 0)
		    {
				String no_dates_text = i18nIcal.getString("no_dates_text");
				JOptionPane.showMessageDialog(null, no_dates_text, "Export2iCal", JOptionPane.INFORMATION_MESSAGE);
		    }
		    else
		    {
		        String nonEmptyTitle = calendarModel.getNonEmptyTitle();
		        if ( nonEmptyTitle.length() == 0)
		        {
		            nonEmptyTitle = "rapla_calendar";
		        }
                String name =nonEmptyTitle +".ics";
		        export(result, name);
		    }
		}).exceptionally((ex) -> {
	          dialogUiFactory.showException(ex, new SwingPopupContext(getMainComponent(), null));
	          return null;
		});
	}
	private void export(String result, String name) throws RaplaException {
		final byte[] bytes = result.getBytes();
        saveFile(bytes, name , new String[] {"ics","ical"});
	}

	public void saveFile(byte[] content, String filename, String[] extensions) throws RaplaException {
		final Frame frame = (Frame) SwingUtilities.getRoot(getMainComponent());
		try {
			ioInterface.saveFile(frame, null, extensions, filename, content);
		} catch (IOException e) {
			throw new RaplaException("Can't export file!", e);
		}
	}


	
}
