package org.rapla.client.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing,id="org.rapla.client.swing.gui.attributeAnnotation")
public interface AnnotationEditAttributeExtension extends AnnotationEdit
{
}
