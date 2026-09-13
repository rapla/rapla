package org.rapla.client.extensionpoints;

import org.rapla.client.swing.EditField;
import org.rapla.entities.Annotatable;
import org.rapla.framework.RaplaException;

import java.util.Collection;

public interface AnnotationEdit
{
    Collection<? extends EditField> createEditFields(Annotatable annotatable);
    void mapTo(EditField field,Annotatable annotatable) throws RaplaException;

    /** Sort key in the type editor; equal keys keep registration order. */
    default int position() { return 0; }
}
