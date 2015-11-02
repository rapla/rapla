package org.rapla.client.swing;

import org.rapla.client.extensionpoints.AnnotationEdit;
import org.rapla.framework.TypedComponentRole;

public interface AnnotationEditExtension extends AnnotationEdit {
    TypedComponentRole<AnnotationEditExtension> ATTRIBUTE_ANNOTATION_EDIT = new TypedComponentRole<AnnotationEditExtension>("org.rapla.client.swing.gui.attributeAnnotation");
    TypedComponentRole<AnnotationEditExtension> CATEGORY_ANNOTATION_EDIT = new TypedComponentRole<AnnotationEditExtension>("org.rapla.client.swing.gui.categoryAnnotation");
    TypedComponentRole<AnnotationEditExtension> DYNAMICTYPE_ANNOTATION_EDIT = new TypedComponentRole<AnnotationEditExtension>("org.rapla.client.swing.gui.typeAnnotation");
}
