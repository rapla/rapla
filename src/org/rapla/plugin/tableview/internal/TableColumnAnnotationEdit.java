package org.rapla.plugin.tableview.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.rapla.entities.Annotatable;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.EditField;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.fields.TextField;
import org.rapla.plugin.tableview.TableViewExtensionPoints;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

public class TableColumnAnnotationEdit extends RaplaGUIComponent implements AnnotationEditExtension{

    
    public TableColumnAnnotationEdit(RaplaContext context) throws Exception {
        super(context);
        
    }

    @Override
    public Collection<? extends EditField> createEditField(Annotatable annotatable) {
        
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
            final Preferences preferences = getClientFacade().getSystemPreferences();
            config = TableConfig.read( preferences, getI18n());
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
        ArrayList<EditField> fields = new ArrayList<EditField>();
        for (TableColumnConfig column:config.getAllColumns())
        {
            final String key = column.getKey();
            final String defaultValue = column.getDefaultValue();
            String label = getLabel( column, getLocale());
            final TextField field = new MyTextField(getContext(), column, label);
            
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
//            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
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

    static String getLabel(TableColumnConfig config, Locale locale)
    {
        final MultiLanguageName name = config.getName();
        if ( name != null)
        {
            String result = name.getName(  locale.getLanguage());
            if ( result != null)
            {
                return result;
            }
        }
        return config.getKey();
    }
    class MyTextField extends TextField 
    {

        TableColumnConfig column;
        public MyTextField(RaplaContext context, TableColumnConfig column,String label)
        {
            super(context, label);
            this.column = column;
        }
        

        
        TableColumnConfig getColumn()
        {
            return column;
        }
        
    }
    
    private Map<String, String> getColumnAnnotations(DynamicType dynamicType)
    {
        final String columnAnnotationPrefix = TableViewExtensionPoints.COLUMN_ANNOTATION;
        Map<String,String> columnAnnotations = new LinkedHashMap<String,String>();
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
            final MyTextField textField = (MyTextField)field;
            @SuppressWarnings("unchecked")
            String value = textField.getValue();
            final String keyName = textField.getColumn().getKey();
            final String columnAnnotationPrefix = TableViewExtensionPoints.COLUMN_ANNOTATION;
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
