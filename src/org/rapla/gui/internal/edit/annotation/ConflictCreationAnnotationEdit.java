package org.rapla.gui.internal.edit.annotation;

import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.EditField;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.fields.ListField;

public class ConflictCreationAnnotationEdit extends RaplaGUIComponent implements AnnotationEditExtension{

    private final String annotationName = DynamicTypeAnnotations.KEY_CONFLICTS;

    public ConflictCreationAnnotationEdit(RaplaContext context) {
        super(context);
    }

    @Override
    public EditField createEditField(Annotatable annotatable) {
        if (!( annotatable instanceof DynamicType))
        {
            return null;
        }
        DynamicType dynamicType = (DynamicType)annotatable;
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        boolean isEventType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        //boolean isResourceType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        if ( !isEventType)
        {
            return null;
        }
        String annotation = annotatable.getAnnotation(annotationName);
        Collection<String> collection = Arrays.asList( new String[] {DynamicTypeAnnotations.VALUE_CONFLICTS_ALWAYS,DynamicTypeAnnotations.VALUE_CONFLICTS_NONE,DynamicTypeAnnotations.VALUE_CONFLICTS_WITH_OTHER_TYPES}); 
        ListField<String> field = new ListField<String>(getContext(), collection);
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
        return field;
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
