package org.rapla.client.extensionpoints;

import java.beans.PropertyChangeListener;

import org.rapla.client.swing.PublishExtension;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.swing, id = PublishExtensionFactory.ID)
public interface PublishExtensionFactory
{
    String ID="publishextension";
	PublishExtension creatExtension(CalendarSelectionModel model, PropertyChangeListener revalidateCallback) throws RaplaException;

    boolean isEnabled();
}