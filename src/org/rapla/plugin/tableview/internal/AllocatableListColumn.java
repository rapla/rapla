package org.rapla.plugin.tableview.internal;

import javax.swing.table.TableColumn;

import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.plugin.tableview.AppointmentTableColumn;

public class AllocatableListColumn extends RaplaComponent implements AppointmentTableColumn {
	public AllocatableListColumn(RaplaContext context) {
		super(context);
	}

	public void init(TableColumn column) {
	    column.setMaxWidth( 130 );
	    column.setPreferredWidth( 130 );
	}

	public Object getValue(AppointmentBlock block) 
	{
		Appointment appointment = block.getAppointment();
		Reservation reservation = appointment.getReservation();
		Allocatable[] allocatablesFor = reservation.getAllocatablesFor(appointment);
		StringBuilder buf = new StringBuilder();
		boolean first = true;
		for (Allocatable alloc: allocatablesFor)
		{
			if ( !contains( alloc))
			{
				continue;
			}
			if (!first)
			{
				buf.append(", ");
			}
			first = false;
			String name = alloc.getName( getLocale());
			buf.append( name);
			
		}
		return buf.toString();
	}

	/**
     * @param alloc  
     */
	protected boolean contains(Allocatable alloc) 
	{
		return true;
	}

	public String getColumnName() {
		return getString("resources");
	}

	public Class<?> getColumnClass() {
		return String.class;
	}

	public String getHtmlValue(AppointmentBlock block) 
	{
		String names = getValue(block).toString();
		return XMLWriter.encode(names);
	}
}