package org.rapla.plugin.tableview.internal;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Named;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.dynamictype.internal.ParsedText;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.extensionpoints.TableColumnDefinitionExtension;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TableConfig
{

    public static final String APPOINTMENTS_VIEW = "appointments";
    public static final String EVENTS_VIEW = "events";
    public static final String APPOINTMENTS_PER_DAY_VIEW = "appointments_per_day";
    private Set<TableColumnConfig> column = new LinkedHashSet<>();
    private Map<String, ViewDefinition> views = new LinkedHashMap<>();

    public TableConfig()
    {

    }
    public static class ViewDefinition implements Named
    {
        private MultiLanguageName name = new MultiLanguageName();
        private final List<String> columns = new ArrayList<>();
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

    private static MultiLanguageName createName(String key, I18nBundle i18n, Set<String> languages, RaplaLocale raplaLocale)
    {
        final MultiLanguageName name = new MultiLanguageName();
        String defaultValue = null;
        if (languages.contains("en"))
        {
            Locale locale = raplaLocale.newLocale("en", null);
            defaultValue = i18n.getString(key, locale);
        }
        for (String lang : languages)
        {
            Locale locale = raplaLocale.newLocale(lang, null);
            final String translation = i18n.getString(key, locale);
            if (defaultValue == null || lang.equals("en") || !translation.equals(defaultValue))
            {
                name.setName(lang, translation);
            }

        }
        return name;
    }

    @NotNull private static TableColumnConfig createPersonsColumn(I18nBundle i18n, Set<String> languages, RaplaLocale raplaLocale)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("persons");
        columnConfig.setDefaultValue("{p->filter(resources(p),r->isPerson(r))}");
        columnConfig.setType("string");
        final MultiLanguageName name = createName("persons", i18n, languages, raplaLocale);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createResourcesColumn(I18nBundle i18n, Set<String> languages, RaplaLocale raplaLocale)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("resources");
        columnConfig.setDefaultValue( "{p->filter(resources(p),r->not(isPerson(r)))}");
        columnConfig.setType("string");
        final MultiLanguageName name = createName("resources", i18n, languages, raplaLocale);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createLastChangedColumn(I18nBundle i18n, Set<String> languages, RaplaLocale raplaLocale)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("lastchanged");
        columnConfig.setDefaultValue("{p->lastchanged(p)}");
        columnConfig.setType("datetime");
        final MultiLanguageName name = createName("last_changed", i18n, languages, raplaLocale);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createEndColumn(I18nBundle i18n, Set<String> languages, RaplaLocale raplaLocale)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("end");
        columnConfig.setDefaultValue( "{p->end(p)}");
        columnConfig.setType("datetime");
        final MultiLanguageName name = createName("end_date", i18n, languages, raplaLocale);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createStartColumn(I18nBundle i18n, Set<String> languages, RaplaLocale raplaLocale)
    {
        String start ="start";
        String defaultValue = "{p->start(p)}";
        String datetime ="datetime";
        String start_date = "start_date";
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey(start);
        columnConfig.setDefaultValue(defaultValue);
        columnConfig.setType(datetime);
        final MultiLanguageName name = createName(start_date, i18n, languages, raplaLocale);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createTimesColumn(I18nBundle i18n, Set<String> languages, RaplaLocale raplaLocale)
    {
        String start ="times";
        String defaultValue = "{p->times(p)}";
        String times = "times";
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey(start);
        columnConfig.setType("string");
        columnConfig.setDefaultValue(defaultValue);
        final MultiLanguageName name = createName(times, i18n, languages, raplaLocale);
        columnConfig.setName(name);
        return columnConfig;
    }

    @NotNull private static TableColumnConfig createNameColumn(I18nBundle i18n, Set<String> languages, RaplaLocale raplaLocale)
    {
        TableColumnConfig columnConfig = new TableColumnConfig();
        columnConfig.setKey("name");
        columnConfig.setDefaultValue("{p->name(p)}");
        columnConfig.setType("string");
        final MultiLanguageName name = createName("name", i18n, languages, raplaLocale);
        i18n.getLocale();
        columnConfig.setName(name);
        return columnConfig;
    }


    //@XmlAccessorType(XmlAccessType.FIELD)
    static public class TableColumnConfig implements Named
    {
		private String key;
        private String type ="string";
        private String defaultValue;
        private MultiLanguageName name;

        private TableColumnConfig addIfNotInResult(TableConfig configResult)
        {
            if ( !configResult.column.contains(this))
            {
                configResult.addColumn( this );
            }
            return this;
        }

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
        
        @Override
		public String toString() {
			return "TableColumnConfig [key=" + key + ", type=" + type + ", defaultValue=" + defaultValue + ", name="
					+ name + "]";
		}

        @Override
        public String getName(Locale locale) {
            if ( name != null) {
                final String name = this.name.getName(locale);
                if ( name != null) {
                    return name;
                }
            }
            return key;
        }
    }

    public void addColumn(TableColumnConfig config)
    {
        this.column.add(config);
    }

    public boolean hasView(String id)
    {
        return views.containsKey(id);
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
        Collection<TableColumnConfig> result = new ArrayList<>();
        final LinkedHashSet<String> orderedColumnKeys = new LinkedHashSet<>(myList.columns);
        Map<String, TableColumnConfig> map = new LinkedHashMap<>();
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

    private static TableConfig read(Preferences preferences, I18nBundle i18n, RaplaLocale raplaLocale, final Set<String> languages,
            Set<TableColumnDefinitionExtension> extensions) throws RaplaException
    {
        RaplaConfiguration configEntry = preferences.getEntry(TableViewPlugin.CONFIG, null);
        try
        {
            final TableConfig config;
            if (configEntry != null)
            {
                config = read(configEntry, raplaLocale);
            }
            else
                {
                config = new TableConfig();
            }
            if ( !config.hasView(EVENTS_VIEW ))
            {
                final ViewDefinition eventsView = config.getOrCreateView(EVENTS_VIEW);
                eventsView.setName(createName("reservations", i18n, languages, raplaLocale));
                eventsView.setContentDefinition("{p->events(p)}");
                eventsView.addColumn(createNameColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
                eventsView.addColumn(createStartColumn(i18n, languages, raplaLocale).addIfNotInResult(config));
                eventsView.addColumn(createLastChangedColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
            }
            if ( !config.hasView(APPOINTMENTS_VIEW))
            {
                final ViewDefinition appointmentsView = config.getOrCreateView(APPOINTMENTS_VIEW);
                appointmentsView.setName(createName("appointments", i18n, languages, raplaLocale));
                appointmentsView.setContentDefinition("{p->appointmentBlocks(p)}");
                appointmentsView.addColumn(createNameColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
                appointmentsView.addColumn(createStartColumn(i18n, languages, raplaLocale).addIfNotInResult(config));
                appointmentsView.addColumn(createEndColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
                appointmentsView.addColumn(createResourcesColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
                appointmentsView.addColumn(createPersonsColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
            }
            if ( !config.hasView(APPOINTMENTS_PER_DAY_VIEW))
            {
                final ViewDefinition appointmentsPerDayView = config.getOrCreateView(APPOINTMENTS_PER_DAY_VIEW);
                appointmentsPerDayView.setName(createName("appointments_per_day", i18n, languages, raplaLocale));
                appointmentsPerDayView.setContentDefinition("{p->appointmentBlocks(p)}");
                appointmentsPerDayView.addColumn(createTimesColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
                appointmentsPerDayView.addColumn(createNameColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
                appointmentsPerDayView.addColumn(createResourcesColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
                appointmentsPerDayView.addColumn(createPersonsColumn(i18n, languages, raplaLocale).addIfNotInResult( config));
            }
            if (configEntry == null ) {
                for (int i = 1; i <= 2; i++) {
                    TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
                    columnConfig.setKey("customColumn_" + i);
                    columnConfig.setType("string");
                    final MultiLanguageName name = createName("unnamed_column", i18n, languages, raplaLocale);
                    columnConfig.setName(name);
                    config.addColumn(columnConfig);
                }
            }
            for (TableColumnDefinitionExtension extension:extensions) {
                final Collection<TableColumnConfig> columns = extension.getColumns(languages);
                for (TableColumnConfig column : columns) {
                    column.addIfNotInResult(config);
                }
            }
            return config;
        }
        catch (ConfigurationException ex)
        {
            throw new RaplaException(ex.getMessage(), ex);
        }
    }

    @NotNull static TableConfig read(RaplaConfiguration configEntry, RaplaLocale raplaLocale) throws ConfigurationException
    {
        TableConfig result = new TableConfig();
        Map<String, TableColumnConfig> columnSet = new HashMap<>();
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
        private final RaplaFacade raplaFacade;
        private final RaplaResources i18n;
        private final RaplaLocale raplaLocale;
        private final Set<TableColumnDefinitionExtension> extensions;
        private final RaplaTableColumnFactory tableColumnCreator;

        @Inject public TableConfigLoader(RaplaFacade raplaFacade, RaplaResources i18n, RaplaLocale raplaLocale,
                Set<TableColumnDefinitionExtension> extensions, RaplaTableColumnFactory tableColumnCreator)
        {
            this.raplaFacade = raplaFacade;
            this.i18n = i18n;
            this.raplaLocale = raplaLocale;
            this.extensions = extensions;
            this.tableColumnCreator = tableColumnCreator;
        }

        public <T, C> List<RaplaTableColumn<T, C>> loadColumns(String configName, User user) throws RaplaException
        {
            List<RaplaTableColumn<T, C>> reservationColumnPlugins = new ArrayList<>();
            final Preferences preferences = raplaFacade.getSystemPreferences();
            TableConfig config = read(preferences, false);
            final Collection<TableColumnConfig> columns = config.getColumns(configName);
            for (final TableColumnConfig column : columns)
            {
                reservationColumnPlugins.add(tableColumnCreator.createColumn(column, user,raplaLocale));
            }
            return reservationColumnPlugins;
        }

        public TableConfig read(Preferences preferences, boolean allLang) throws RaplaException
        {
            final Set<String> strings = allLang ? new HashSet<>(raplaLocale.getAvailableLanguages()) : Collections.singleton(i18n.getLang());
            return TableConfig.read(preferences, i18n, raplaLocale, strings, extensions);
        }
    }

}
