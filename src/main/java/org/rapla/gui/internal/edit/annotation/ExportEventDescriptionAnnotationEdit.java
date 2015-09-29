package org.rapla.gui.internal.edit.annotation;


import org.rapla.client.extensionpoints.AnnotationEditTypeExtension;
import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

import javax.inject.Inject;

import org.rapla.client.extensionpoints.AnnotationEdit;
import org.rapla.gui.EditField;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.fields.TextField;
import org.rapla.inject.Extension;


@Extension(provides= AnnotationEditTypeExtension.class, id="exporteventdescription")
public class ExportEventDescriptionAnnotationEdit extends RaplaGUIComponent implements AnnotationEdit
{
    protected String annotationName = DynamicTypeAnnotations.KEY_DESCRIPTION_FORMAT_EXPORT;
    protected String DEFAULT_VALUE = "";
    
    @Inject
    public ExportEventDescriptionAnnotationEdit(RaplaContext context) {
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
        if ( classificationType == null || !(classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION) ))
        {
            return null;
        }
        String annotation = annotatable.getAnnotation(annotationName);
        TextField field = new TextField(getContext(),getString(annotationName));
        if ( annotation != null)
        {
            field.setValue( annotation);
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
            String value = ((TextField)field).getValue();
            if ( value != null && !value.equals(DEFAULT_VALUE))
            {
                annotatable.setAnnotation(annotationName, value.toString());
                return;
            }
        }
        annotatable.setAnnotation(annotationName, null);
        
    }

}
