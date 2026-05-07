package org.rapla.client.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id=AnnotationEditAttributeExtension.ID)
public interface AnnotationEditAttributeExtension extends AnnotationEdit
{
    String ID = "org.rapla.client.swing.gui.attributeAnnotation";
}
