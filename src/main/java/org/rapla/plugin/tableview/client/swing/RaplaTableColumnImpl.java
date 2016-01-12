package org.rapla.plugin.tableview.client.swing;

import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.internal.AbstractRaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

import javax.swing.table.TableColumn;

public class RaplaTableColumnImpl<T> extends AbstractRaplaTableColumn<T, TableColumn>
{
    protected RaplaTableColumnImpl(TableColumnConfig column, RaplaLocale raplaLocale,ClientFacade facade)
    {
        super(column, raplaLocale, facade);
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