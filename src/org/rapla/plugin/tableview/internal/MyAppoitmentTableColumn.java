package org.rapla.plugin.tableview.internal;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.AppointmentTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

public class MyAppoitmentTableColumn extends AbstractTableColumn<AppointmentBlock> implements AppointmentTableColumn
{
    public MyAppoitmentTableColumn(TableColumnConfig column, RaplaLocale raplaLocale)
    {
        super(column, raplaLocale);
    }

    @Override
    public Object getValue(AppointmentBlock block)
    {
        return format(block);
    }

    @Override
    public String getHtmlValue(AppointmentBlock block)
    {
        final Object value = getValue(block);
        return formatHtml(value);
    }
    	    
}