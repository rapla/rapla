package org.rapla.client.extensionpoints;

import org.rapla.entities.Annotatable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.EditField;

public interface AnnotationEdit
{
    EditField createEditField(Annotatable annotatable);
    void mapTo(EditField field,Annotatable annotatable) throws RaplaException;
}
