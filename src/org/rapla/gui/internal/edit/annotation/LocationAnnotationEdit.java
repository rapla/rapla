package org.rapla.gui.internal.edit.annotation;

import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.EditField;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.fields.BooleanField;

public class LocationAnnotationEdit extends RaplaGUIComponent implements AnnotationEditExtension{

    private final String annotationName = DynamicTypeAnnotations.KEY_LOCATION;

    public LocationAnnotationEdit(RaplaContext context) {
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
        //isEventType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        boolean isResourceType = classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        if ( !isResourceType)
        {
            return null;
        }
        String annotation = annotatable.getAnnotation(annotationName);
        BooleanField field = new BooleanField(getContext(),getString("is_location"));
        if ( annotation != null)
        {
            field.setValue( annotation.equalsIgnoreCase("true"));
        }
        return field;
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException 
    {
        if ( field != null)
        {
            Boolean value = ((BooleanField)field).getValue();
            if ( value != null && value == true)
            {
                annotatable.setAnnotation(annotationName, Boolean.TRUE.toString());
                return;
            }
        }
        annotatable.setAnnotation(annotationName, null);
    }

}
