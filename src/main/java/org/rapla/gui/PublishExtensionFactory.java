package org.rapla.gui;

import java.beans.PropertyChangeListener;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing, id="publishextension")
public interface PublishExtensionFactory
{
	PublishExtension creatExtension(CalendarSelectionModel model, PropertyChangeListener revalidateCallback) throws RaplaException;
}