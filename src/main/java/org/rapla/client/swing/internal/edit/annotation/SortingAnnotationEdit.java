package org.rapla.client.swing.internal.edit.annotation;

import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditAttributeExtension;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.ListField;
import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides= AnnotationEditAttributeExtension.class, id=AttributeAnnotations.KEY_SORTING)
public class SortingAnnotationEdit extends RaplaGUIComponent implements AnnotationEditAttributeExtension
{

    private final String annotationName = AttributeAnnotations.KEY_SORTING;
    String NOTHING_SELECTED = "nothing_selected";
    
    @Inject
    public SortingAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        super(facade, i18n, raplaLocale, logger);
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable) {
        if (!( annotatable instanceof Attribute))
        {
            return Collections.emptyList();
        }
        Attribute attribute = (Attribute)annotatable;
        DynamicType dynamicType = attribute.getDynamicType();
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if ( classificationType == null || !(classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON ) || classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)))
        {
            return Collections.emptyList();
        }
        String annotation = annotatable.getAnnotation(annotationName);
        Collection<String> collection = Arrays.asList(NOTHING_SELECTED,AttributeAnnotations.VALUE_SORTING_ASCENDING,
                AttributeAnnotations.VALUE_SORTING_DESCENDING);
        ListField<String> field = new ListField<String>(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), collection);
        field.setFieldName( getString(annotationName));
        
        if (annotation  == null)
        {
            annotation =  NOTHING_SELECTED;
        }
        field.setValue( annotation);
        @SuppressWarnings("serial")
        DefaultListCellRenderer renderer = new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            
                if ( value != null)
                {
                    String lookupString = "sorting."+value.toString();
                    value =getString(lookupString);
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            }
        };
        field.setRenderer( renderer);
        return Collections.singleton(field);
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException 
    {
        if ( field != null)
        {
            @SuppressWarnings("unchecked")
            String value = ((ListField<String>)field).getValue();
            if ( value == null || value.equals( NOTHING_SELECTED))
            {
                annotatable.setAnnotation(annotationName, null);
            }
            else
            {
                annotatable.setAnnotation(annotationName, value);
            }
        }
        else
        {
            annotatable.setAnnotation(annotationName, null);
        }
    }

}
