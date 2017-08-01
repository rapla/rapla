package org.rapla.plugin.tableview.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;

import java.util.Collection;
import java.util.Set;

@ExtensionPoint(context = InjectionContext.all,id= TableViewPlugin.PLUGIN_ID)
public interface TableColumnDefinitionExtension
{
    Collection<TableConfig.TableColumnConfig> getColumns(Set<String> languages);
}
