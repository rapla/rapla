package org.rapla.plugin.tableview.client.swing;

import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.rapla.RaplaResources;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.client.swing.extensionpoints.AppointmentSummaryExtension;

@Extension(provides = AppointmentSummaryExtension.class,id = "appointmentcounter")
public final class AppointmentCounter implements AppointmentSummaryExtension
{
	private final RaplaResources i18n;

    @Inject
	public AppointmentCounter(RaplaResources i18n) 
	{
        this.i18n = i18n;
	}

	public void init(final JTable table, JPanel summaryRow) {
		final JLabel counter = new JLabel();
		summaryRow.add( counter);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			
			public void valueChanged(ListSelectionEvent arg0) 
			{
				int count = table.getSelectedRows().length;
				counter.setText( count+ " " + (count == 1 ? i18n.getString("appointment") : i18n.getString("appointments")) + " ");
			}
		});
	}
}