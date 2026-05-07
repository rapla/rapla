package org.rapla.client.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=AnnotationEditCategoryExtension.ID)
public interface AnnotationEditCategoryExtension extends AnnotationEdit
{
    String ID = "org.rapla.client.swing.gui.categoryAnnotation";
}
