package org.rapla.plugin.tableview.internal;

import javax.swing.table.TableColumn;

import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.plugin.tableview.AppointmentTableColumn;

public final class AppointmentNameColumn extends RaplaComponent implements AppointmentTableColumn {
	public AppointmentNameColumn(RaplaContext context) {
		super(context);
	}

	public void init(TableColumn column) {
	
	}

	public Object getValue(AppointmentBlock block) 
	{
		//	getLocale().
		Appointment appointment = block.getAppointment();
		Reservation reservation = appointment.getReservation();
		return reservation.format(getLocale(),DynamicTypeAnnotations.KEY_NAME_FORMAT, block);
	}

	public String getColumnName() {
		return getString("name");
	}

	public Class<?> getColumnClass() {
		return String.class;
	}

	public String getHtmlValue(AppointmentBlock block) {
		String value = getValue(block).toString();
		return XMLWriter.encode(value);		       

	}
}