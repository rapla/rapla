package org.rapla.plugin.tableview.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

//@XmlAccessorType(XmlAccessType.FIELD)
public class TableConfig
{
    private Set<TableColumnConfig> column = new LinkedHashSet<TableColumnConfig>();
    private Map<String, MyList> views = new LinkedHashMap<String, MyList>();
    //private int[] testArray = new int[] {1,2};

    private static class MyList 
    {
        List<String> item = new ArrayList<>();
    }
    
    static TableConfig DEFAULT = new TableConfig();
    static
    {
        TableConfig config = DEFAULT;
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("name");
            columnConfig.setDefaultValue("{p->name(p)}");
            columnConfig.setType("string");
            final MultiLanguageName name = new MultiLanguageName();
            name.setName("en", "name");
            name.setName("de", "Name");
            columnConfig.setName(name);
            config.addColumn(columnConfig);
            
            config.addView("events", columnConfig);
            config.addView("appointments", columnConfig);
        }
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("start");
            columnConfig.setDefaultValue("{p->start(p)}");
            columnConfig.setType("datetime");
            final MultiLanguageName name = new MultiLanguageName();
            name.setName("en", "start");
            name.setName("de", "Start");
            columnConfig.setName(name);
            config.addColumn(columnConfig);
            
            config.addView("events", columnConfig);
            config.addView("appointments", columnConfig);
        }
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("end");
            columnConfig.setDefaultValue("{p->end(p)}");
            columnConfig.setType("datetime");
            final MultiLanguageName name = new MultiLanguageName();
            name.setName("en", "end");
            name.setName("de", "End");
            columnConfig.setName(name);
            config.addColumn(columnConfig);
            
            config.addView("appointments", columnConfig);
            
        }
        
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("lastchanged");
            columnConfig.setDefaultValue("{p->lastchanged(p)}");
            columnConfig.setType("datetime");
            final MultiLanguageName name = new MultiLanguageName();
            name.setName("en", "last changed");
            name.setName("de", "zuletzt geaendert");
            columnConfig.setName(name);
            config.addColumn(columnConfig);
            
            config.addView("events", columnConfig);
        }
        
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("resources");
            columnConfig.setDefaultValue("{p->filter(resources(p),r->not(isPerson(r)))}");
            columnConfig.setType("string");
            final MultiLanguageName name = new MultiLanguageName();
            name.setName("en", "resources");
            name.setName("de", "Ressourcen");
            columnConfig.setName(name);
            config.addColumn(columnConfig);
            
            config.addView("appointments", columnConfig);
        }
        
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("persons");
            columnConfig.setDefaultValue("{p->filter(resources(p),r->isPerson(r))}");
            columnConfig.setType("string");
            final MultiLanguageName name = new MultiLanguageName();
            name.setName("en", "persons");
            name.setName("de", "Personen");
            columnConfig.setName(name);
            config.addColumn(columnConfig);
            config.addView("appointments", columnConfig);
        }
        for(int i = 1; i <= 3; i++)
        {
            TableConfig.TableColumnConfig columnConfig = new TableConfig.TableColumnConfig();
            columnConfig.setKey("customColumn_"+i);
            columnConfig.setType("string");
            final MultiLanguageName name = new MultiLanguageName();
            columnConfig.setName(name);
            config.addColumn(columnConfig);
        }
        
        
    }
    
    static public TableConfig getDefaultConfig()
    {
        return DEFAULT;
    }
    
    //@XmlAccessorType(XmlAccessType.FIELD)
    static public class TableColumnConfig
    {
        private String key;
        private String type;
        private String defaultValue;
        private MultiLanguageName name;

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
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

    public void addView(String view, TableColumnConfig config)
    {
        final boolean contains = column.contains(config);
        if (!contains)
        {
            throw new IllegalStateException("ColumnConfig not found. Please use addColumn before ");
        }
        final String key = config.getKey();
        addItem(view, key);
        //views.put( view , list);
    }
    
    public Set<TableColumnConfig> getAllColumns()
    {
        return column;
    }
    
    // returns null of the tablecolumnconfig is not found
    public Collection<TableColumnConfig> getColumns(String viewKey)
    {
        final MyList myList = views.get( viewKey);
        if ( myList == null)
        {
            return null;
        }
        Collection<TableColumnConfig> result = new ArrayList<TableColumnConfig>();
        Map<String, TableColumnConfig> map = new LinkedHashMap<String,TableColumnConfig>();
        final LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>(myList.item);
        for ( TableColumnConfig col:column)
        {
           final String key = col.getKey();
           if ( linkedHashSet.contains( key))
           {
               map.put(key, col);
           }
        }
        for ( String key:linkedHashSet)
        {
            final TableColumnConfig column = map.get(key);
            if ( column != null)
            {
                result.add( column);
            }
        }
        return result;
    }

    protected void addItem(String view, final String key)
    {
        MyList list = views.get(view);// != null ? new ArrayList(Arrays.asList( views.get(view))): null;
        if (list == null)
        {
            list = new MyList();
            views.put(view, list);
        }
        list.item.add(key);
    }

    public boolean removeView(String view)
    {
        return views.remove(view) != null;
    }
    
    public static TableConfig read( Preferences preferences) throws RaplaException
    {
        RaplaConfiguration configEntry = preferences.getEntry( TableViewPlugin.CONFIG, null);
        try
        {
            TableConfig config = configEntry != null ? read( configEntry) : TableConfig.getDefaultConfig();
            return config;
        }
        catch ( ConfigurationException ex)
        {
            throw new RaplaException(ex.getMessage(),ex);
        }
    }

    public static TableConfig read(RaplaConfiguration config) throws ConfigurationException
    {
        TableConfig result = new TableConfig();
        Set<String> columnSet = new HashSet<>(); 
        for (Configuration columnConfig : config.getChildren("column"))
        {
            String key = columnConfig.getChild("key").getValue();
            String type = columnConfig.getChild("type").getValue("string");
            String defaultValue = columnConfig.getChild("defaultValue").getValue("");
            MultiLanguageName name = new MultiLanguageName();
            TableConfig.TableColumnConfig column = new TableConfig.TableColumnConfig();
            column.setKey( key);;
            column.setType( type);
            column.setDefaultValue( defaultValue);
            for (Configuration nameConfig : columnConfig.getChild("name").getChild("mapLocales").getChildren("entry"))
            {
                String language = nameConfig.getChild("key").getValue();
                String translation = nameConfig.getChild("value").getValue();
                name.setName(language, translation);
            }
            column.setName( name);
            result.addColumn( column);
            columnSet.add( key);
        }
        for ( Configuration viewConfig : config.getChild("views").getChildren())
        {
            String key = viewConfig.getChild("key").getValue();
            for (Configuration item:viewConfig.getChild("value").getChildren())
            {
                String itemTest = item.getValue();
                if ( !columnSet.contains(itemTest))
                {
                    throw new ConfigurationException("item with key " + itemTest + " not found ");
                }
                result.addItem( key ,itemTest );
            }
        }
        return result;
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
            final DefaultConfiguration mapLocales = multiLanguageName.getMutableChild("mapLocales");
            final MultiLanguageName name = column.getName();
            for ( String language: name.getAvailableLanguages())
            {
                DefaultConfiguration entry = new DefaultConfiguration("entry");
                String translation = name.getName( language);
                entry.getMutableChild("key").setValue(language);
                entry.getMutableChild("value").setValue(translation);
                mapLocales.addChild( entry);
            }
            result.addChild( col);
        }
        final DefaultConfiguration views =result.getMutableChild("views");
        for (String key:config.views.keySet())
        {
            DefaultConfiguration entry = new DefaultConfiguration("entry");
            entry.getMutableChild("key").setValue(key);
            final DefaultConfiguration list = entry.getMutableChild("value");
            final Collection<TableColumnConfig> columns = config.getColumns( key);
            for (TableColumnConfig column:columns)
            {
                DefaultConfiguration item = new DefaultConfiguration("item");
                final String columnKey = column.getKey();
                item.setValue(columnKey);
                list.addChild(item);    
            }
            views.addChild( entry);
            
        }
        return result;
    }

    public void removeColumn(TableColumnConfig columnConfig)
    {
        column.remove( columnConfig);
    }
}
