package org.rapla.plugin.tableview.internal;

import java.util.Date;

import javax.swing.table.TableColumn;

import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.DateCellRenderer;
import org.rapla.plugin.tableview.ReservationTableColumn;

public class ReservationLastChangedColumn extends RaplaComponent implements ReservationTableColumn {
		
	public ReservationLastChangedColumn(RaplaContext context) {
		super(context);
	}

	public void init(TableColumn column) {
        column.setCellRenderer( new DateCellRenderer( getRaplaLocale()));
        column.setMaxWidth( 130 );
        column.setPreferredWidth( 130 );
	}
	
	public Object getValue(Reservation reservation) {
		return reservation.getLastChanged();
	}
	
	public String getColumnName() {
		return getString("last_changed");
	}
	
	public Class<?> getColumnClass() {
		return Date.class;
	}

	public String getHtmlValue(Reservation reservation) 
	{
		RaplaLocale raplaLocale = getRaplaLocale();
		final Date lastChangeTime = reservation.getLastChanged();
		String lastChanged= raplaLocale.formatDateLong(lastChangeTime);
		return lastChanged;
	}
}