package org.rapla.gui.internal.edit.annotation;

import java.util.*;

import org.rapla.entities.Annotatable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.client.extensionpoints.AnnotationEdit;
import org.rapla.gui.EditField;
import org.rapla.gui.internal.edit.AbstractEditUI;

public class AnnotationEditUI extends AbstractEditUI<Annotatable>
{
    Set<? extends AnnotationEdit> annotationExtensions;
    Map<AnnotationEdit,EditField> fieldMap = new HashMap<AnnotationEdit,EditField>();
    
    public AnnotationEditUI(RaplaContext context, Set<? extends AnnotationEdit> annotationExtensions) {
        super(context);
        this.annotationExtensions = annotationExtensions;
    }
    
    @Override
    public void mapToObjects() throws RaplaException {
    }

    @Override
    protected void mapFromObjects() throws RaplaException {
        List<EditField> fields = new ArrayList<EditField>();
        Annotatable annotatable = objectList.get(0);
        for ( AnnotationEdit annot: annotationExtensions)
        {
            EditField field = annot.createEditField(annotatable);
            if ( field != null)
            {
                fields.add( field);
                fieldMap.put(  annot, field);
            }
        }
        setFields(fields);
    }

    
    public void mapTo(List<Annotatable> annotatables) throws RaplaException {
        Annotatable annotatable = annotatables.get(0);
        for ( AnnotationEdit annot: annotationExtensions)
        {
            EditField field = fieldMap.get( annot);
            annot.mapTo(field, annotatable);
        }
    }

}
