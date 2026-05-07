package org.rapla.client.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=AnnotationEditTypeExtension.ID)
public interface AnnotationEditTypeExtension extends AnnotationEdit
{
    String ID = "org.rapla.client.swing.gui.typeAnnotation";
}
