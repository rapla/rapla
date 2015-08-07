package org.rapla.plugin.tableview.internal;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;

public final class EventCounter extends RaplaComponent implements SummaryExtension {
	public EventCounter(RaplaContext context) 
	{
		super(context);
	}

	public void init(final JTable table, JPanel summaryRow) {
		final JLabel counter = new JLabel();
		summaryRow.add( counter);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			
			public void valueChanged(ListSelectionEvent arg0) 
			{
				int count = table.getSelectedRows().length;
				counter.setText( count+ " " + (count == 1 ? getString("reservation") : getString("reservations")) + " " );
			}
		});
	}
}