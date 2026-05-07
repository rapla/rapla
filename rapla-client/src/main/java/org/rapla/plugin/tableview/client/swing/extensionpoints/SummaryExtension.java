package org.rapla.plugin.tableview.client.swing.extensionpoints;

import javax.swing.JPanel;
import javax.swing.JTable;

public interface SummaryExtension 
{
	void init(JTable table, JPanel summaryRow);
}
