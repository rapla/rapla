package org.rapla.client.swing.internal.edit.annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.AnnotationEdit;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.internal.edit.AbstractEditUI;
import org.rapla.entities.Annotatable;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public class AnnotationEditUI extends AbstractEditUI<Annotatable>
{
    Set<? extends AnnotationEdit> annotationExtensions;
    Map<AnnotationEdit,Collection<? extends EditField>> fieldMap = new HashMap<AnnotationEdit,Collection<? extends EditField>>();
    
    public AnnotationEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, Set<? extends AnnotationEdit> annotationExtensions) {
        super(facade, i18n, raplaLocale, logger);
        this.annotationExtensions = annotationExtensions;
    }
    
    @Override
    public void mapToObjects() throws RaplaException {
    }

    @Override
    protected void mapFromObjects() throws RaplaException
    {
        List<EditField> fields = new ArrayList<EditField>();
        Annotatable annotatable = objectList.get(0);
        for (AnnotationEdit annot : annotationExtensions)
        {
            final Collection<? extends EditField> annotationFields = annot.createEditFields(annotatable);
            fieldMap.put(annot, annotationFields);
            for (EditField field : annotationFields)
            {
                fields.add(field);

            }
        }
        setFields(fields);
    }

    public void mapTo(List<Annotatable> annotatables) throws RaplaException
    {
        Annotatable annotatable = annotatables.get(0);
        for (AnnotationEdit annot : annotationExtensions)
        {
            Collection<? extends EditField> fields = fieldMap.get(annot);
            for (EditField field : fields)
            {
                annot.mapTo(field, annotatable);
            }
        }
    }

}
