package org.rapla.plugin.tableview.client.gwt;

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

    @Override
    public RaplaTableColumn createColumn(TableColumnConfig column, RaplaLocale raplaLocale)
    {
        return new AbstractRaplaTableColumn(column, raplaLocale)
        {
            @Override
            public void init(Object column)
            {

            }
        };
    }
}
