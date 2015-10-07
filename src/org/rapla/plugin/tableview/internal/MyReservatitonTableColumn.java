package org.rapla.plugin.tableview.internal;

import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.ReservationTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

public final class MyReservatitonTableColumn extends AbstractTableColumn<Reservation> implements ReservationTableColumn
{
    public MyReservatitonTableColumn(TableColumnConfig column,RaplaLocale raplaLocale)
    {
       super( column, raplaLocale);
    }
    
    public Object getValue(Reservation reservation)
    {
        return format(reservation);
    }
    
    public String getHtmlValue(Reservation object)
    {
        Object value = getValue(object);
        return formatHtml(value);
    }
}