package org.rapla.client.extensionpoints;

import java.util.Collection;

import org.rapla.client.swing.EditField;
import org.rapla.entities.Annotatable;
import org.rapla.framework.RaplaException;

public interface AnnotationEdit
{
    Collection<? extends EditField> createEditFields(Annotatable annotatable);
    void mapTo(EditField field,Annotatable annotatable) throws RaplaException;
}
