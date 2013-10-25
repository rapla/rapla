package org.rapla.plugin.tableview.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.table.DefaultTableModel;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.domain.Reservation;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.ReservationTableColumn;

public class ReservationTableModel extends DefaultTableModel
{
    private static final long serialVersionUID = 1L;
    
    Reservation[] reservations = new Reservation[] {};
    Locale locale;
    I18nBundle i18n;
    Map<Integer,ReservationTableColumn> columns = new LinkedHashMap<Integer, ReservationTableColumn>();
    
    //String[] columns;
    public ReservationTableModel(Locale locale, I18nBundle i18n, Collection<? extends ReservationTableColumn> reservationColumnPlugins) {
        this.locale = locale;
        this.i18n = i18n;
        List<String> columnNames = new ArrayList<String>(); 
        int column = 0;
        for (ReservationTableColumn col: reservationColumnPlugins)
        {
        	columnNames.add( col.getColumnName());
        	columns.put( column, col);
        	column++;
        }
        this.setColumnIdentifiers( columnNames.toArray());
    }

    public void setReservations(Reservation[] events) {
        this.reservations = events;
        super.fireTableDataChanged();
    }

    public Reservation getReservationAt(int row) {
        return this.reservations[row];
    }

    public boolean isCellEditable(int row, int column) {
        return false;
    }

    public int getRowCount() {
        if ( reservations != null)
            return reservations.length;
        else
            return 0;
    }

    public Object getValueAt( int rowIndex, int columnIndex )
    {
        Reservation event = reservations[rowIndex];
        RaplaTableColumn<Reservation> tableColumn = columns.get( columnIndex);
        return tableColumn.getValue(event);
    }

    public Class<?> getColumnClass(int columnIndex) {
    	RaplaTableColumn<Reservation> tableColumn = columns.get( columnIndex);
        return tableColumn.getColumnClass();
    }



}
