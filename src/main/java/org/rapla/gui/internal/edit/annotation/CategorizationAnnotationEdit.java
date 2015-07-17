package org.rapla.gui.internal.edit.annotation;

import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.EditField;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.fields.BooleanField;

public class CategorizationAnnotationEdit extends RaplaGUIComponent implements AnnotationEditExtension{

    final String annotationName = AttributeAnnotations.KEY_CATEGORIZATION;

    public CategorizationAnnotationEdit(RaplaContext context) {
        super(context);
    }

    @Override
    public EditField createEditField(Annotatable annotatable) {
        if (!( annotatable instanceof Attribute))
        {
            return null;
        }
        Attribute attribute = (Attribute)annotatable;
        String annotation = annotatable.getAnnotation(annotationName);
        BooleanField field = new BooleanField(getContext(),getString(annotationName));
        if ( annotation != null)
        {
            field.setValue( annotation.equalsIgnoreCase("true"));
        }
        else
        {
            if ( attribute.getKey().equalsIgnoreCase(annotationName))
            {
                field.setValue( true );
            }
        }
        return field;
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException 
    {
        if ( field != null)
        {
            Boolean value = ((BooleanField)field).getValue();
            if ( value != null )
            {
                if ( value)
                {
                    annotatable.setAnnotation(annotationName, Boolean.TRUE.toString());
                    return;
                }
                else if (( (Attribute)annotatable).getKey().equals(annotationName))
                {
                    annotatable.setAnnotation(annotationName, Boolean.FALSE.toString());
                    return;
                }

            }
        }
        annotatable.setAnnotation(annotationName, null);
    }

}
