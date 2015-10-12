package org.rapla.plugin.tableview.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Named;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.dynamictype.internal.ParsedText;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.*;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.extensionpoints.TableColumnDefinitionExtension;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.table.TableColumn;
import java.util.*;

public class TableConfig
{
    private Set<TableColumnConfig> column = new LinkedHashSet<TableColumnConfig>();
    private Map<String, ViewDefinition> views = new LinkedHashMap<String, ViewDefinition>();

    public static class ViewDefinition implements Named
    {
        private MultiLanguageName name = new MultiLanguageName();
        private final List<String> columns = new ArrayList<String>();
        private ParsedText contentDefinition = new ParsedText("{appointmentBlocks()}");

        public List<String> getColumns()
        {
            return Collections.unmodifiableList(columns);
        }

        public void addColumn(TableColumnConfig config)
        {
            if (!columns.contains(config.getKey()))
            {
                columns.add(config.getKey());
            }
        }

        public boolean removeColumn(TableColumnConfig config)
        {
            return columns.remove(config.getKey());
        }

        private void setContentDefinition(String formatString)
        {
            contentDefinition = new ParsedText(formatString);
        }

        private MultiLanguageName getName()
        {
            return name;
        }

        private void setName(MultiLanguageName name)
        {
            this.name = name;
        }

        public String getContentDefinition()
        {
            return contentDefinition.toString();
        }

        @Override public String getName(Locale locale)
        {
            return name.getName(locale);
        }
    }

    private static MultiLanguageName createName(String key, I18nBundle i18n, Set<String> languages)
    {
        final MultiLanguageName name = new MultiLanguageName();
        String defaultValue = null;
        if (languages.contains("en"))
        {
            Locale locale = new Locale("en");
            defaultValue = i18n.getString(key, locale);
        }
        for (String lang : languages)
        {
            Locale locale = new Locale(lang);
            final String translation = i18n.getString(key, locale);
            if (defaultValue == null || lang.equals("en") || !translation.equals(defaultValue))
            {
                name.setName(lang, translation);
            }

        }
        return name;
    }

    private static TableConfig createDefault(I18nBundle i18n, Set<String> languages)
    {
        TableConfig config = new TableConfig();
        final ViewDefinition appointmentsView = config.getOrCreateView("appointments");
        appointmentsView.setName(createName("appointments", i18n, languages));
        appointmentsView.setContentDefinition("{p->appointmentBlocks(p)}");
        final ViewDefinition eventsView = config.getOrCreateView("events");
        eventsView.setName(createName("reservations", i18n, languages));
        eventsView.setContentDefinition("{p->events(p)}");
        {
            TableColumnConfig columnConfig = createNameColumn(i18n, languages);
            config.addColumn(columnConfig);
            eventsView.addColumn(columnConfig);
            appointmentsView.addColumn(columnConfig);
        }
        {
            TableColumnConfig columnConfig = createStartColumn(i18n, languages);
            config.addColumn(columnConfig);
            eventsView.addColumn(columnConfig);
            appointmentsView.addColumn(columnConfig);
        }
        {
            TableColumnConfig columnConfig = createEndColumn(i18n, languages);
            config.addColumn(columnConfig);
            appointmentsView.addColumn(columnConfig);
        }
        {
            TableColumnConfig columnConfig = createLastChangedColumn(i18n, languages);
            config.addColumn(columnConfig);
            eventsView.addColumn(columnConfig);
        }
        {
            TableColumnConfig columnConfig = createResourcesColumn(i18n, languages);
            config.addColumn(columnConfig);
            appointmentsView.addColumn(columnConfig);
        }
        {
            TableColumnConfig columnConfig = createPersonsColumn(i18n, languages);
            config.addColumn(columnConfig);
            appointmentsView.addColumn(columnConfig);
        }
        for (int i = 1; i <= 2; i++)
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("customColumn_" + i);
            columnConfig.setType("string");
            final MultiLanguageName name = createName("unnamed_column", i18n, languages);
            columnConfig.setName(name);
            config.addColumn(columnConfig);
        }
        return config;
    }

    @NotNull private static TableColumnConfig createPersonsColumn(I18nBundle i18n, Set<String> languages)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("persons");
        columnConfig.setDefaultValue("{p->filter(resources(p),r->isPerson(r))}");
        columnConfig.setType("string");
        final MultiLanguageName name = createName("persons", i18n, languages);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createResourcesColumn(I18nBundle i18n, Set<String> languages)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("resources");
        columnConfig.setDefaultValue( "{p->filter(resources(p),r->not(isPerson(r)))}");
        columnConfig.setType("string");
        final MultiLanguageName name = createName("resources", i18n, languages);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createLastChangedColumn(I18nBundle i18n, Set<String> languages)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("lastchanged");
        columnConfig.setDefaultValue("{p->lastchanged(p)}");
        columnConfig.setType("datetime");
        final MultiLanguageName name = createName("last_changed", i18n, languages);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createEndColumn(I18nBundle i18n, Set<String> languages)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("end");
        columnConfig.setDefaultValue( "{p->end(p)}");
        columnConfig.setType("datetime");
        final MultiLanguageName name = createName("end_date", i18n, languages);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createStartColumn(I18nBundle i18n, Set<String> languages)
    {
        String start ="start";
        String defaultValue = "{p->start(p)}";
        String datetime ="datetime";
        String start_date = "start_date";
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey(start);
        columnConfig.setDefaultValue(defaultValue);
        columnConfig.setType(datetime);
        final MultiLanguageName name = createName(start_date, i18n, languages);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createNameColumn(I18nBundle i18n, Set<String> languages)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("name");
        columnConfig.setDefaultValue("{p->name(p)}");
        columnConfig.setType("string");
        final MultiLanguageName name = createName("name", i18n, languages);
        i18n.getLocale();
        columnConfig.setName(name);
        return columnConfig;
    }

    //@XmlAccessorType(XmlAccessType.FIELD)
    static public class TableColumnConfig
    {
        private String key;
        private String type;
        private String defaultValue;
        private MultiLanguageName name;

        @Override public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            return result;
        }

        @Override public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            TableColumnConfig other = (TableColumnConfig) obj;
            if (key == null)
            {
                if (other.key != null)
                    return false;
            }
            else if (!key.equals(other.key))
                return false;
            return true;
        }

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getDefaultValue()
        {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue)
        {
            this.defaultValue = defaultValue;
        }

        public MultiLanguageName getName()
        {
            return name;
        }

        public void setName(MultiLanguageName name)
        {
            this.name = name;
        }

    }

    public void addColumn(TableColumnConfig config)
    {
        this.column.add(config);
    }

    public ViewDefinition getOrCreateView(String id)
    {
        if (!views.containsKey(id))
        {
            final ViewDefinition definition = new ViewDefinition();
            views.put(id, definition);
        }
        return views.get(id);
    }

    public Set<TableColumnConfig> getAllColumns()
    {
        return column;
    }

    public Map<String, ViewDefinition> getViewMap()
    {
        return Collections.unmodifiableMap(views);
    }

    // returns null of the tablecolumnconfig is not found
    public Collection<TableColumnConfig> getColumns(String viewKey)
    {
        final ViewDefinition myList = views.get(viewKey);
        if (myList == null)
        {
            return null;
        }
        Collection<TableColumnConfig> result = new ArrayList<TableColumnConfig>();
        final LinkedHashSet<String> orderedColumnKeys = new LinkedHashSet<String>(myList.columns);
        Map<String, TableColumnConfig> map = new LinkedHashMap<String, TableColumnConfig>();
        for (TableColumnConfig col : column)
        {
            final String key = col.getKey();
            if (orderedColumnKeys.contains(key))
            {
                map.put(key, col);
            }
        }
        // build the result in the order of the orderedColumnKeys
        for (String key : orderedColumnKeys)
        {
            final TableColumnConfig column = map.get(key);
            if (column != null)
            {
                result.add(column);
            }
            else
            {
                //column key in view defined but not found in column definition
            }
        }
        return result;
    }

    //    protected void addItem(String view, final String key)
    //    {
    //        ViewDefinition list = views.get(view);// != null ? new ArrayList(Arrays.asList( views.get(view))): null;
    //        if (list == null)
    //        {
    //            list = new ViewDefinition();
    //            if (view.equals("events"))
    //            {
    //                list.content = new ParsedText("events");
    //                //list.name = createName("events", i, languages)
    //            }
    //            if (view.equals("appointments"))
    //            {
    //
    //            }
    //            views.put(view, list);
    //        }
    //        list.columns.add(key);
    //    }
    //
    public boolean removeView(String view)
    {
        return views.remove(view) != null;
    }

    private static TableConfig read(Preferences preferences, I18nBundle i18n, final Set<String> languages,
            Set<TableColumnDefinitionExtension> extensions) throws RaplaException
    {
        RaplaConfiguration configEntry = preferences.getEntry(TableViewPlugin.CONFIG, null);
        try
        {
            final TableConfig configResult;
            if (configEntry != null)
            {
                TableConfig result = read(configEntry);
                configResult = result;
                addIfNotInResult( createNameColumn(i18n, languages), configResult);
                addIfNotInResult( createStartColumn(i18n, languages), configResult);
                addIfNotInResult( createEndColumn(i18n, languages), configResult);
                addIfNotInResult( createLastChangedColumn(i18n, languages), configResult);
                addIfNotInResult( createResourcesColumn(i18n, languages), configResult);
                addIfNotInResult( createPersonsColumn(i18n, languages), configResult);
            }
            else
            {
                configResult = createDefault(i18n, languages);
            }
            for (TableColumnDefinitionExtension extension:extensions)
            {
                final Collection<TableColumnConfig> columns = extension.getColumns(languages);
                for ( TableColumnConfig column:columns)
                {
                    addIfNotInResult( column, configResult);
                }

            }

            return configResult;
        }
        catch (ConfigurationException ex)
        {
            throw new RaplaException(ex.getMessage(), ex);
        }
    }

    private static void addIfNotInResult(TableColumnConfig column, TableConfig configResult)
    {
        if ( !configResult.column.contains(column))
        {
            configResult.addColumn( column );
        }
    }

    @NotNull static TableConfig read(RaplaConfiguration configEntry) throws ConfigurationException
    {
        TableConfig result = new TableConfig();
        Map<String, TableColumnConfig> columnSet = new HashMap<String, TableColumnConfig>();
        for (Configuration columnConfig : configEntry.getChildren("column"))
        {
            String key = columnConfig.getChild("key").getValue();
            String type = columnConfig.getChild("type").getValue("string");
            String defaultValue = columnConfig.getChild("defaultValue").getValue("");
            TableColumnConfig column = new TableColumnConfig();
            column.setKey(key);
            column.setType(type);
            column.setDefaultValue(defaultValue);
            final Configuration nameChild = columnConfig.getChild("name");
            MultiLanguageName name = readName(nameChild);
            column.setName(name);
            result.addColumn(column);
            columnSet.put(key, column);
        }
        for (Configuration viewConfig : configEntry.getChild("views").getChildren())
        {
            String key = viewConfig.getChild("key").getValue();
            final String contentDefinintion = viewConfig.getChild("contentDefinition").getValue();
            final ViewDefinition view = result.getOrCreateView(key);

            view.setContentDefinition(contentDefinintion);
            final Configuration nameChild = viewConfig.getChild("name");
            MultiLanguageName name = readName(nameChild);
            view.setName(name);
            for (Configuration item : viewConfig.getChild("value").getChildren())
            {
                String itemTest = item.getValue();
                TableColumnConfig column = columnSet.get(itemTest);
                if (column == null)
                {
                    throw new ConfigurationException("item with key " + itemTest + " not found ");
                }
                view.addColumn(column);
            }
        }
        return result;
    }

    private static MultiLanguageName readName(final Configuration nameChild) throws ConfigurationException
    {
        MultiLanguageName name = new MultiLanguageName();
        for (Configuration nameConfig : nameChild.getChild("mapLocales").getChildren("entry"))
        {
            String language = nameConfig.getChild("key").getValue();
            String translation = nameConfig.getChild("value").getValue();
            name.setName(language, translation);
        }
        return name;
    }

    public static RaplaConfiguration print(TableConfig config)
    {
        final RaplaConfiguration result = new RaplaConfiguration("config");
        for (TableConfig.TableColumnConfig column : config.column)
        {
            final DefaultConfiguration col = new DefaultConfiguration("column");
            col.getMutableChild("key").setValue(column.getKey());
            col.getMutableChild("defaultValue").setValue(column.getDefaultValue());
            col.getMutableChild("type").setValue(column.getType());
            final DefaultConfiguration multiLanguageName = col.getMutableChild("name");
            final MultiLanguageName name = column.getName();
            write(name, multiLanguageName);
            result.addChild(col);
        }
        final DefaultConfiguration views = result.getMutableChild("views");
        for (String key : config.views.keySet())
        {
            final ViewDefinition viewDefinition = config.views.get(key);
            DefaultConfiguration entry = new DefaultConfiguration("entry");
            entry.getMutableChild("key").setValue(key);
            final DefaultConfiguration multiLanguageName = entry.getMutableChild("name");
            final MultiLanguageName name = viewDefinition.getName();
            write(name, multiLanguageName);
            entry.getMutableChild("contentDefinition").setValue(viewDefinition.getContentDefinition());
            final DefaultConfiguration list = entry.getMutableChild("value");
            final Collection<TableColumnConfig> columns = config.getColumns(key);
            for (TableColumnConfig column : columns)
            {
                DefaultConfiguration item = new DefaultConfiguration("item");
                final String columnKey = column.getKey();
                item.setValue(columnKey);
                list.addChild(item);
            }
            views.addChild(entry);

        }
        return result;
    }

    private static void write(final MultiLanguageName name, final DefaultConfiguration multiLanguageName)
    {
        final DefaultConfiguration mapLocales = multiLanguageName.getMutableChild("mapLocales");
        for (String language : name.getAvailableLanguages())
        {
            DefaultConfiguration entry = new DefaultConfiguration("entry");
            String translation = name.getName(language);
            entry.getMutableChild("key").setValue(language);
            entry.getMutableChild("value").setValue(translation);
            mapLocales.addChild(entry);
        }
    }

    public void removeColumn(TableColumnConfig columnConfig)
    {
        column.remove(columnConfig);
    }

    @Singleton public static class TableConfigLoader
    {
        private final ClientFacade clientFacade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Set<TableColumnDefinitionExtension> extensions;

        @Inject public TableConfigLoader(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale,
                Set<TableColumnDefinitionExtension> extensions)
        {
            this.clientFacade = clientFacade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.extensions = extensions;
        }

        public <T> List<RaplaTableColumn<T, TableColumn>> loadColumns(String configName) throws RaplaException
        {
            List<RaplaTableColumn<T, TableColumn>> reservationColumnPlugins = new ArrayList<RaplaTableColumn<T, TableColumn>>();
            final Preferences preferences = clientFacade.getSystemPreferences();
            TableConfig config = read(preferences, false);
            final Collection<TableColumnConfig> columns = config.getColumns(configName);
            for (final TableColumnConfig column : columns)
            {
                reservationColumnPlugins.add(new RaplaTableColumnImpl(column, raplaLocale));
            }
            return reservationColumnPlugins;
        }

        public TableConfig read(Preferences preferences, boolean allLang) throws RaplaException
        {
            final Set<String> strings = allLang ? new HashSet<String>(raplaLocale.getAvailableLanguages()) : Collections.singleton(i18n.getLang());
            return TableConfig.read(preferences, i18n, strings, extensions);
        }
    }

}
