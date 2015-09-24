package org.rapla.plugin.tableview.internal;

import java.util.Date;

import javax.inject.Inject;
import javax.swing.table.TableColumn;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.client.swing.DateCellRenderer;
import org.rapla.plugin.tableview.extensionpoints.AppointmentTableColumn;

@Extension(provides = AppointmentTableColumn.class, id = "start")
public final class AppointmentStartDate extends RaplaComponent implements AppointmentTableColumn {
    @Inject
	public AppointmentStartDate(RaplaContext context) {
		super(context);
	}

	public void init(TableColumn column) {
	    column.setCellRenderer( new DateCellRenderer( getRaplaLocale()));
	    column.setMaxWidth( 175 );
	    column.setPreferredWidth( 175 );
	}

	public Object getValue(AppointmentBlock block) {
		return new Date(block.getStart());
	}

	public String getColumnName() {
		return getString("start_date");
	}

	public Class<?> getColumnClass() {
		return Date.class;
	}

	public String getHtmlValue(AppointmentBlock block) 
	{
		RaplaLocale raplaLocale = getRaplaLocale();
		final Date date = new Date(block.getStart());
		if ( block.getAppointment().isWholeDaysSet())
        {
		    String dateString= raplaLocale.formatDateLong(date);
		    return dateString;
        }
		else
		{
            String dateString= raplaLocale.formatDateLong(date) +  " " + raplaLocale.formatTime( date);
	        return dateString;
		}
	}
}