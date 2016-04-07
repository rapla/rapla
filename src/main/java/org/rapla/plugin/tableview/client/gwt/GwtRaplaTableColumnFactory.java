package org.rapla.plugin.tableview.client.gwt;

import javax.inject.Inject;

import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.internal.AbstractRaplaTableColumn;
import org.rapla.plugin.tableview.internal.RaplaTableColumnFactory;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

@DefaultImplementation(context = { InjectionContext.gwt }, of = RaplaTableColumnFactory.class)
public class GwtRaplaTableColumnFactory implements RaplaTableColumnFactory
{
    final RaplaFacade facade;
    @Inject
    public GwtRaplaTableColumnFactory(RaplaFacade facade)
    {
        this.facade = facade;
    }

    @Override
    public RaplaTableColumn createColumn(TableColumnConfig column, User user,RaplaLocale raplaLocale)
    {
        return new AbstractRaplaTableColumn(column, raplaLocale, facade, user)
        {
            @Override
            public void init(Object column)
            {

            }
        };
    }
}
