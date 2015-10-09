package org.rapla.client.extensionpoints;

import java.util.Collection;

import org.rapla.entities.Annotatable;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EditField;

public interface AnnotationEdit
{
    Collection<? extends EditField> createEditFields(Annotatable annotatable);
    void mapTo(EditField field,Annotatable annotatable) throws RaplaException;
}
