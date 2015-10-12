package org.rapla.plugin.eventtimecalculator;

import org.rapla.entities.MultiLanguageName;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.extensionpoints.TableColumnDefinitionExtension;
import org.rapla.plugin.tableview.internal.TableConfig;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

@Extension(provides = TableColumnDefinitionExtension.class,id=EventTimeCalculatorPlugin.PLUGIN_ID)
public class EventtimeCalculatorColumnDefinitionExtension implements TableColumnDefinitionExtension
{
    private  final EventTimeCalculatorResources i18n;

    @Inject
    public EventtimeCalculatorColumnDefinitionExtension(EventTimeCalculatorResources i18n)
    {
        this.i18n = i18n;
    }

    @Override public Collection<TableConfig.TableColumnConfig> getColumns(Set<String> languages)
    {
        TableConfig.TableColumnConfig column = new TableConfig.TableColumnConfig();
        MultiLanguageName name = new MultiLanguageName();
        column.setName(name);
        column.setKey(DurationFunctions.NAMESPACE +":duration");
        column.setDefaultValue("{p->" + DurationFunctions.NAMESPACE + ":duration(p)}");
        column.setType("string");
        for (String lang:languages)
        {
            Locale locale = new Locale( lang);
            name.setName(lang, i18n.getString("duration", locale));
        }
        return Collections.singletonList(column);
    }
}
