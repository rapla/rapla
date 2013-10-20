package org.rapla.plugin.tableview.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.table.DefaultTableModel;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.plugin.tableview.AppointmentTableColumn;

public class AppointmentTableModel extends DefaultTableModel
{
    private static final long serialVersionUID = 1L;
    
    List<AppointmentBlock> appointments= new ArrayList<AppointmentBlock>();
    Locale locale;
    I18nBundle i18n;
    Map<Integer,AppointmentTableColumn> columns = new LinkedHashMap<Integer, AppointmentTableColumn>();
    
    //String[] columns;
    public AppointmentTableModel(Locale locale, I18nBundle i18n, Collection<? extends AppointmentTableColumn> columnPlugins) {
        this.locale = locale;
        this.i18n = i18n;
        List<String> columnNames = new ArrayList<String>(); 
        int column = 0;
        for (AppointmentTableColumn col: columnPlugins)
        {
        	columnNames.add( col.getColumnName());
        	columns.put( column, col);
        	column++;
        }
        this.setColumnIdentifiers( columnNames.toArray());
    }

    public void setAppointments(List<AppointmentBlock> appointments2) {
        this.appointments = appointments2;
        super.fireTableDataChanged();
    }

    public AppointmentBlock getAppointmentAt(int row) {
        return this.appointments.get(row);
    }

    public boolean isCellEditable(int row, int column) {
        return false;
    }

    public int getRowCount() {
        if ( appointments != null)
            return appointments.size();
        else
            return 0;
    }

    public Object getValueAt( int rowIndex, int columnIndex )
    {
        AppointmentBlock event = getAppointmentAt(rowIndex);
        AppointmentTableColumn tableColumn = columns.get( columnIndex);
        return tableColumn.getValue(event);
    }

    public Class<?> getColumnClass(int columnIndex) {
    	AppointmentTableColumn tableColumn = columns.get( columnIndex);
        return tableColumn.getColumnClass();
    }



}
