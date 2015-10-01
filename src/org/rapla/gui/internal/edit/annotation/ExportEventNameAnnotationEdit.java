package org.rapla.gui.internal.edit.annotation;


import java.util.Collection;
import java.util.Collections;

import org.rapla.entities.Annotatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.EditField;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.edit.fields.TextField;

public class ExportEventNameAnnotationEdit extends RaplaGUIComponent implements AnnotationEditExtension {
    protected String annotationName = DynamicTypeAnnotations.KEY_NAME_FORMAT_EXPORT;
    protected String DEFAULT_VALUE = "";
    
    public ExportEventNameAnnotationEdit(RaplaContext context) {
        super(context);
    }

    @Override
    public Collection<? extends EditField> createEditField(Annotatable annotatable) {
        if (!( annotatable instanceof DynamicType))
        {
            return Collections.emptyList();
        }
        DynamicType dynamicType = (DynamicType)annotatable;
        String classificationType = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if ( classificationType == null || !(classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION) ))
        {
            return Collections.emptyList();
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
        return Collections.singleton(field);
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
