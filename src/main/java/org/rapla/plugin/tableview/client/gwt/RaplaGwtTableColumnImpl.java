package org.rapla.plugin.tableview.client.gwt;

import org.rapla.entities.User;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.internal.AbstractRaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;


public class RaplaGwtTableColumnImpl<T> extends AbstractRaplaTableColumn<T, Object>
{
    public RaplaGwtTableColumnImpl(TableColumnConfig column, RaplaLocale raplaLocale, ClientFacade facade, User user)
    {
        super(column, raplaLocale, facade.getRaplaFacade(),user);
    }

    @Override
    public void init(Object column)
    {
    }
}