package org.rapla.plugin.tableview.internal;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditTypeExtension;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.entities.Annotatable;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Extension(provides= AnnotationEditTypeExtension.class, id="tableColumn")
public class TableColumnAnnotationEdit extends RaplaGUIComponent implements AnnotationEditTypeExtension {


    private final TableConfig.TableConfigLoader tableConfigLoader;
    private final TextFieldFactory textFieldFactory;
    private final Map<TextField, TableColumnConfig> textFieldsToColumnConfig = new HashMap<>();
    @Inject
    public TableColumnAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TableConfig.TableConfigLoader tableConfigLoader, TextFieldFactory textFieldFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.tableConfigLoader = tableConfigLoader;
        this.textFieldFactory = textFieldFactory;
        
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable)
    {
        if (!( annotatable instanceof DynamicType))
        {
            return Collections.emptyList();
        }
        DynamicType dynamicType = (DynamicType)annotatable;
        Map<String, String> columnAnnotations = getColumnAnnotations(dynamicType);
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        boolean isEventType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        //boolean isResourceType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        if ( !isEventType)
        {
            return Collections.emptyList();
        }
        TableConfig config;
        try
        {
            final Preferences preferences = getFacade().getSystemPreferences();
            config = tableConfigLoader.read(preferences,false);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
        ArrayList<EditField> fields = new ArrayList<>();
        textFieldsToColumnConfig.clear();
        for (TableColumnConfig column:config.getAllColumns())
        {
            final String key = column.getKey();
            final String defaultValue = column.getDefaultValue();
            String label = column.getName( getLocale());
            final TextField field = textFieldFactory.create(label);
            textFieldsToColumnConfig.put(field, column);
//            new MyTextField(getContext(), column, label);
            
            fields.add(field);
            String value = columnAnnotations.get( key);
            if ( value == null || value.length() == 0)
            {
                value = defaultValue;
            }
            field.setValue( value);
        }
//        String annotation = annotatable.getAnnotation(annotationName);
//        Collection<String> collection = Arrays.asList( new String[] {DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS,DynamicTypeAnnotations.VALUE_CONFLICTS_NONE,DynamicTypeAnnotations.VALUE_CONFLICTS_WITH_OTHER_TYPES}); 
//        ListField<String> field = new ListField<String>(getContext(), collection);
//        field.setFieldName( getString(annotationName));
//        
//        if (annotation  == null)
//        {
//            annotation =  DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS;
//        }
//        field.setValue( annotation);
//        @SuppressWarnings("serial")
//        DefaultListCellRenderer renderer = new DefaultListCellRenderer()
//        {
//            @Override
//            public ServerComponent getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
//            
//                if ( value != null)
//                {
//                    String lookupString = "conflicts."+value.toString();
//                    value =getString(lookupString);
//                }
//                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
//
//            }
//        };
//        field.setRenderer( renderer);
//        return field;
        return fields;
    }

//    class MyTextField extends TextField
//    {
//
//        TableColumnConfig column;
//        public MyTextField(, TableColumnConfig column,String label)
//        {
//            super(context, label);
//            this.column = column;
//        }
//        
//
//        
//        TableColumnConfig getColumn()
//        {
//            return column;
//        }
//        
//    }
//    
    private Map<String, String> getColumnAnnotations(DynamicType dynamicType)
    {
        final String columnAnnotationPrefix = TableViewPlugin.COLUMN_ANNOTATION;
        Map<String,String> columnAnnotations = new LinkedHashMap<>();
        for ( String annotationKey:dynamicType.getAnnotationKeys())
        {
               if ( annotationKey.startsWith(columnAnnotationPrefix))
               {
                   String suffix = annotationKey.substring( columnAnnotationPrefix.length());
                   String columnFormat = dynamicType.getAnnotation(annotationKey);
                   columnAnnotations.put( suffix,columnFormat);
               }
        }
        return columnAnnotations;
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException 
    {
        if (!( annotatable instanceof DynamicType))
        {
            return ;
        }
        DynamicType dynamicType = (DynamicType)annotatable;
        final Map<String, String> columnAnnotations = getColumnAnnotations(dynamicType);
        if ( field != null)
        {
            final TableColumnConfig columnConfig = textFieldsToColumnConfig.get(field);
            final TextField textField = (TextField)field;
            @SuppressWarnings("unchecked")
            String value = textField.getValue();
            final String keyName = columnConfig.getKey();
            final String columnAnnotationPrefix = TableViewPlugin.COLUMN_ANNOTATION;
            String annotationName = columnAnnotationPrefix + keyName;
            if ( value != null)
            {
                annotatable.setAnnotation(annotationName, value);
            }
            else
            {
                annotatable.setAnnotation(annotationName, null);
            }
        }
        else
        {
            for ( String key:columnAnnotations.keySet())
            {
                annotatable.setAnnotation(key, null);
            }
        }
    }

}
