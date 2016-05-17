package org.rapla.client.swing.internal.edit.annotation;

import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.inject.Inject;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEditTypeExtension;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.edit.fields.ListField;
import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;

@Extension(provides= AnnotationEditTypeExtension.class, id=DynamicTypeAnnotations.KEY_CONFLICTS)
public class ConflictCreationAnnotationEdit extends RaplaGUIComponent implements AnnotationEditTypeExtension
{

    private final String annotationName = DynamicTypeAnnotations.KEY_CONFLICTS;

    @Inject
    public ConflictCreationAnnotationEdit(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger) {
        super(facade, i18n, raplaLocale, logger);
    }

    @Override
    public Collection<? extends EditField> createEditFields(Annotatable annotatable) {
        if (!( annotatable instanceof DynamicType))
        {
            return Collections.emptyList();
        }
        DynamicType dynamicType = (DynamicType)annotatable;
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        boolean isEventType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        //boolean isResourceType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        if ( !isEventType)
        {
            return Collections.emptyList();
        }
        String annotation = annotatable.getAnnotation(annotationName);
        Collection<String> collection = Arrays.asList(DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS,DynamicTypeAnnotations.VALUE_CONFLICTS_NONE,DynamicTypeAnnotations.VALUE_CONFLICTS_WITH_OTHER_TYPES);
        ListField<String> field = new ListField<String>(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(), collection);
        field.setFieldName( getString(annotationName));
        
        if (annotation  == null)
        {
            annotation =  DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS;
        }
        field.setValue( annotation);
        @SuppressWarnings("serial")
        DefaultListCellRenderer renderer = new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            
                if ( value != null)
                {
                    String lookupString = "conflicts."+value.toString();
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
            String conflicts = ((ListField<String>)field).getValue();
            if ( conflicts == null || conflicts.equals( DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS))
            {
                annotatable.setAnnotation(annotationName, null);
            }
            else
            {
                annotatable.setAnnotation(annotationName, conflicts);
            }
        }
        else
        {
            annotatable.setAnnotation(annotationName, null);
        }
    }

}
