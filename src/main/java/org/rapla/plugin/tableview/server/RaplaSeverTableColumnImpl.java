package org.rapla.plugin.tableview.server;

import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.client.swing.DateCellRenderer;
import org.rapla.plugin.tableview.internal.AbstractRaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

import javax.swing.table.TableColumn;

public class RaplaSeverTableColumnImpl<T> extends AbstractRaplaTableColumn<T, Object>
{
    protected RaplaSeverTableColumnImpl(TableColumnConfig column, RaplaLocale raplaLocale, RaplaFacade facade, User user)
    {
        super(column, raplaLocale, facade,user);
    }

    @Override
    public void init(Object column)
    {
    }
}