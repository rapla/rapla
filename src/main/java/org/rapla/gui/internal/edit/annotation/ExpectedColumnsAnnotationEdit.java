package org.rapla.gui.internal.edit.annotation;

import javax.inject.Inject;

import org.rapla.client.extensionpoints.AnnotationEdit;
import org.rapla.client.extensionpoints.AnnotationEditAttributeExtension;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.internal.edit.fields.TextField;
import org.rapla.inject.Extension;

@Extension(provides= AnnotationEditAttributeExtension.class, id="expectedcolums")
public class ExpectedColumnsAnnotationEdit extends ExpectedRowsAnnotationEdit implements AnnotationEditAttributeExtension
{
    
    @Inject
    public ExpectedColumnsAnnotationEdit(RaplaContext context) {
        super(context);
        annotationName = AttributeAnnotations.KEY_EXPECTED_COLUMNS;
        DEFAULT_VALUE = new Long(TextField.DEFAULT_LENGTH);
    }


}
