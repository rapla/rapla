package org.rapla.gui.internal.edit.annotation;

import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.EditField;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.fields.LongField;

public class ExpectedRowsAnnotationEdit extends RaplaGUIComponent implements AnnotationEditExtension {
    protected String annotationName = AttributeAnnotations.KEY_EXPECTED_ROWS;
    protected Long DEFAULT_VALUE = new Long(1);
    
    public ExpectedRowsAnnotationEdit(RaplaContext context) {
        super(context);
    }

    @Override
    public EditField createEditField(Annotatable annotatable) {
        if (!( annotatable instanceof Attribute))
        {
            return null;
        }
        Attribute attribute = (Attribute)annotatable;
        AttributeType type = attribute.getType();
        if ( type!=AttributeType.STRING)
        {
            return null;
        }
        String annotation = annotatable.getAnnotation(annotationName);
        LongField field = new LongField(getContext(),getString(annotationName));
        if ( annotation != null)
        {
            field.setValue( Integer.parseInt(annotation));
        }
        else
        {
            field.setValue( DEFAULT_VALUE);
        }
        addCopyPaste(field.getComponent());
        return field;
    }

    @Override
    public void mapTo(EditField field, Annotatable annotatable) throws RaplaException {
        if ( field != null)
        {
            Long value = ((LongField)field).getValue();
            if ( value != null && !value.equals(DEFAULT_VALUE))
            {
                annotatable.setAnnotation(annotationName, value.toString());
                return;
            }
        }
        annotatable.setAnnotation(annotationName, null);
        
    }

}
