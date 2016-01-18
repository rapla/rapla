package org.rapla.plugin.tableview.internal;

import org.rapla.entities.User;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

public interface RaplaTableColumnFactory
{
    RaplaTableColumn createColumn(TableColumnConfig column, User user,RaplaLocale raplaLocale);
}
