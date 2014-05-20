package org.rapla.gui.internal.edit.annotation;

import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.internal.edit.fields.TextField;

public class ExpectedColumnsAnnotationEdit extends ExpectedRowsAnnotationEdit implements AnnotationEditExtension {
    
    public ExpectedColumnsAnnotationEdit(RaplaContext context) {
        super(context);
        annotationName = AttributeAnnotations.KEY_EXPECTED_COLUMNS;
        DEFAULT_VALUE = new Long(TextField.DEFAULT_LENGTH);
    }


}
