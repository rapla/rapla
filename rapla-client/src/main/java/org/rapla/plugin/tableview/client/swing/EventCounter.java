package org.rapla.plugin.tableview.client.swing;

import org.rapla.RaplaResources;
import org.rapla.plugin.tableview.client.swing.extensionpoints.ReservationSummaryExtension;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

@Service

public final class EventCounter implements ReservationSummaryExtension
{
	private final RaplaResources i18n;

    @Autowired
	public EventCounter(RaplaResources i18n) 
	{
        this.i18n = i18n;
	}

	public void init(final JTable table, JPanel summaryRow) {
		final JLabel counter = new JLabel();
		summaryRow.add( counter);
		summaryRow.add( Box.createHorizontalStrut(30));
		table.getSelectionModel().addListSelectionListener((evt)-> counter.setText( formatCount( table.getSelectedRows().length)));
	}

	private String formatCount(int count)
	{
		return count+ " " + (count == 1 ? i18n.getString("reservation") : i18n.getString("reservations"));
	}
}