package org.rapla.plugin.tableview.client.swing;

import javax.inject.Inject;

import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.internal.RaplaTableColumnFactory;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

@DefaultImplementation(context = { InjectionContext.server, InjectionContext.swing }, of = RaplaTableColumnFactory.class)
public class SwingRaplaTableColumnFactory implements RaplaTableColumnFactory
{

    final ClientFacade facade;
    @Inject
    public SwingRaplaTableColumnFactory(ClientFacade facade)
    {
        super();
        this.facade = facade;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public RaplaTableColumn createColumn(TableColumnConfig column, RaplaLocale raplaLocale)
    {
        return new RaplaTableColumnImpl(column, raplaLocale, facade);
    }

}
