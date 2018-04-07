package org.rapla.plugin.tableview.server;

import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.internal.RaplaTableColumnFactory;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

import javax.inject.Inject;

@DefaultImplementation(context = { InjectionContext.server}, of = RaplaTableColumnFactory.class)
public class ServerTableColumnFactory implements RaplaTableColumnFactory
{

    final RaplaFacade facade;
    @Inject
    public ServerTableColumnFactory(RaplaFacade facade)
    {
        super();
        this.facade = facade;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public RaplaTableColumn createColumn(TableColumnConfig column, User user,RaplaLocale raplaLocale)
    {
        return new RaplaSeverTableColumnImpl(column, raplaLocale, facade, user);
    }

}
