package org.rapla.plugin.tableview.client.swing;

import org.rapla.entities.User;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.internal.AbstractRaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

import javax.swing.table.TableColumn;

public class RaplaSwingTableColumnImpl<T> extends AbstractRaplaTableColumn<T, TableColumn>
{
    protected RaplaSwingTableColumnImpl(TableColumnConfig column, RaplaLocale raplaLocale, ClientFacade facade, User user)
    {
        super(column, raplaLocale, facade.getRaplaFacade(),user);
    }

    @Override
    public void init(TableColumn column)
    {
        if ( isDate())
        {
            final DateCellRenderer cellRenderer = new DateCellRenderer(getRaplaLocale(), false);
            column.setCellRenderer(cellRenderer);
            column.setMaxWidth( 120 );
            column.setPreferredWidth( 120 );
        }
        else if ( isDatetime())
        {
            final DateCellRenderer cellRenderer = new DateCellRenderer(getRaplaLocale(), true);
            column.setCellRenderer(cellRenderer);
            column.setMaxWidth( 175 );
            column.setPreferredWidth( 175 );
        }
        // TODO Auto-generated method stub
    }
}