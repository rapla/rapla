package org.rapla.client.extensionpoints;

import org.rapla.client.swing.PublishExtension;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import java.beans.PropertyChangeListener;

@ExtensionPoint(context = InjectionContext.swing, id="publishextension")
public interface PublishExtensionFactory
{
	PublishExtension creatExtension(CalendarSelectionModel model, PropertyChangeListener revalidateCallback) throws RaplaException;

    boolean isEnabled();
}