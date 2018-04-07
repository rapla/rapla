package org.rapla.plugin.tableview.client.gwt;

import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.internal.RaplaTableColumnFactory;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;
import org.rapla.plugin.tableview.server.RaplaSeverTableColumnImpl;

import javax.inject.Inject;

@DefaultImplementation(context = { InjectionContext.gwt}, of = RaplaTableColumnFactory.class)
public class GwtTableColumnFactory implements RaplaTableColumnFactory
{

    final ClientFacade facade;
    @Inject
    public GwtTableColumnFactory(ClientFacade facade)
    {
        super();
        this.facade = facade;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public RaplaTableColumn createColumn(TableColumnConfig column, User user,RaplaLocale raplaLocale)
    {
        return new RaplaGwtTableColumnImpl(column, raplaLocale, facade, user);
    }

}
