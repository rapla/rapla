package org.rapla.plugin.tableview.client.swing;

import javax.swing.table.TableColumn;

import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.internal.AbstractRaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

public class RaplaTableColumnImpl<T> extends AbstractRaplaTableColumn<T, TableColumn>
{
    protected RaplaTableColumnImpl(TableColumnConfig column, RaplaLocale raplaLocale)
    {
        super(column, raplaLocale);
    }

    @Override
    public void init(TableColumn column)
    {
        if ( isDate())
        {
            column.setCellRenderer( new DateCellRenderer( getRaplaLocale()));
            column.setMaxWidth( 130 );
            column.setPreferredWidth( 130 );
        }
        else if ( isDatetime())
        {
            column.setCellRenderer( new DateCellRenderer( getRaplaLocale()));
            column.setMaxWidth( 175 );
            column.setPreferredWidth( 175 );
        }
        // TODO Auto-generated method stub
    }
}