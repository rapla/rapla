package org.rapla.gui;

import java.util.Collection;

import org.rapla.entities.Annotatable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

public interface AnnotationEditExtension {
    TypedComponentRole<AnnotationEditExtension> ATTRIBUTE_ANNOTATION_EDIT = new TypedComponentRole<AnnotationEditExtension>("org.rapla.gui.attributeAnnotation");
    TypedComponentRole<AnnotationEditExtension> CATEGORY_ANNOTATION_EDIT = new TypedComponentRole<AnnotationEditExtension>("org.rapla.gui.categoryAnnotation");
    TypedComponentRole<AnnotationEditExtension> DYNAMICTYPE_ANNOTATION_EDIT = new TypedComponentRole<AnnotationEditExtension>("org.rapla.gui.typeAnnotation");

    Collection<? extends EditField> createEditField(Annotatable annotatable) throws RaplaException;
   
    void mapTo(EditField field,Annotatable annotatable) throws RaplaException;
}
