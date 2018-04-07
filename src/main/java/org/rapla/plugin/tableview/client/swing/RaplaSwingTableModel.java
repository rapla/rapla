package org.rapla.plugin.tableview.client.swing;

import org.rapla.plugin.tableview.RaplaTableModel;

import javax.swing.table.DefaultTableModel;

public class RaplaSwingTableModel extends DefaultTableModel
{
    RaplaTableModel raplaTableModel;

    public RaplaSwingTableModel(RaplaTableModel raplaTableModel)
    {
        this.raplaTableModel = raplaTableModel;
        final Object[] columnNames = raplaTableModel.getColumnNames().toArray();
        this.setColumnIdentifiers( columnNames);
    }

    @Override
    public int getRowCount()
    {
        if ( raplaTableModel == null)
        {
            return 0;
        }
        return raplaTableModel.getRowCount();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        return raplaTableModel.getColumnClass(columnIndex);
    }

    @Override
    public Object getValueAt(int row, int column)
    {
        return raplaTableModel.getValueAt(row, column);
    }

    public boolean isCellEditable(int row, int column) {
        return false;
    }

}
