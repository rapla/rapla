package org.rapla.plugin.tableview.client.swing;

import javax.inject.Inject;

import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.internal.RaplaTableColumnFactory;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

@DefaultImplementation(context = { InjectionContext.swing }, of = RaplaTableColumnFactory.class)
public class SwingTableColumnFactory implements RaplaTableColumnFactory
{

    final ClientFacade facade;
    @Inject
    public SwingTableColumnFactory(ClientFacade facade)
    {
        super();
        this.facade = facade;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public RaplaTableColumn createColumn(TableColumnConfig column, User user,RaplaLocale raplaLocale)
    {
        return new RaplaSwingTableColumnImpl(column, raplaLocale, facade, user);
    }

}
