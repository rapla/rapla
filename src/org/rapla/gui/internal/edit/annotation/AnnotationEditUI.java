package org.rapla.gui.internal.edit.annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rapla.entities.Annotatable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.AnnotationEditExtension;
import org.rapla.gui.EditField;
import org.rapla.gui.internal.edit.AbstractEditUI;

public class AnnotationEditUI extends AbstractEditUI<Annotatable> 
{
    Collection<AnnotationEditExtension> annotationExtensions;
    Map<AnnotationEditExtension,Collection<? extends EditField>> fieldMap = new HashMap<AnnotationEditExtension,Collection<? extends EditField>>();
    
    public AnnotationEditUI(RaplaContext context, Collection<AnnotationEditExtension> annotationExtensions) {
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
        for ( AnnotationEditExtension annot: annotationExtensions)
        {
            final Collection<? extends EditField> annotationFields = annot.createEditField(annotatable);
            fieldMap.put(  annot, annotationFields);
            for (EditField field:annotationFields)
            {
                fields.add( field);
                
            }
        }
        setFields(fields);
    }

    
    public void mapTo(List<Annotatable> annotatables) throws RaplaException {
        Annotatable annotatable = annotatables.get(0);
        for ( AnnotationEditExtension annot: annotationExtensions)
        {
            Collection<? extends EditField> fields = fieldMap.get( annot);
            for ( EditField field:fields)
            {
                annot.mapTo(field, annotatable);
            }
        }
    }

}
