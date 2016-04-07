package org.rapla.plugin.tableview.extensionpoints;

import java.util.Collection;
import java.util.Set;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;

@ExtensionPoint(context = InjectionContext.all,id= TableViewPlugin.PLUGIN_ID)
public interface TableColumnDefinitionExtension
{
    Collection<TableConfig.TableColumnConfig> getColumns(Set<String> languages);
}
