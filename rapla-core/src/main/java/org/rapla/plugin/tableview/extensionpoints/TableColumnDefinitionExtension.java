package org.rapla.plugin.tableview.extensionpoints;

import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;

import java.util.Collection;
import java.util.Set;


public interface TableColumnDefinitionExtension
{
    Collection<TableConfig.TableColumnConfig> getColumns(Set<String> languages);
}
