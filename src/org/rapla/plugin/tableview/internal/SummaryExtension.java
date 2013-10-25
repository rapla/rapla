package org.rapla.plugin.tableview.internal;

import javax.swing.JPanel;
import javax.swing.JTable;

public interface SummaryExtension 
{
	void init(JTable table, JPanel summaryRow);
}
