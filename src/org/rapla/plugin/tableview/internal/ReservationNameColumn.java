package org.rapla.plugin.tableview.internal;

import javax.swing.table.TableColumn;

import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.NameFormatUtil;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.plugin.tableview.ReservationTableColumn;

public final class ReservationNameColumn extends RaplaComponent implements ReservationTableColumn{
	public ReservationNameColumn(RaplaContext context) {
		super(context);
	}

	public void init(TableColumn column) {
	
	}

	public Object getValue(Reservation reservation) 
	{
		return NameFormatUtil.getName(reservation,getLocale());
	}
	

	public String getColumnName() {
		return getString("name");
	}

	public Class<?> getColumnClass() {
		return String.class;
	}

	public String getHtmlValue(Reservation event) {
		String value = NameFormatUtil.getExportName(event, getLocale());
		return XMLWriter.encode(value);		       

	}

	
}