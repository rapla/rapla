package org.rapla.plugin.tableview.server;

import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.internal.DefaultRaplaTableColumn;
import org.rapla.plugin.tableview.internal.RaplaTableColumnFactory;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

import org.springframework.beans.factory.annotation.Autowired;

public class ServerTableColumnFactory implements RaplaTableColumnFactory
{

    final RaplaFacade facade;
    @Autowired
    public ServerTableColumnFactory(RaplaFacade facade)
    {
        super();
        this.facade = facade;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public RaplaTableColumn createColumn(TableColumnConfig column, User user,RaplaLocale raplaLocale)
    {
        return new DefaultRaplaTableColumn(column, raplaLocale, facade, user);
    }

}
