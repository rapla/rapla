package org.rapla.client.extensionpoints;

import org.rapla.client.swing.PublishExtension;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaException;

import java.beans.PropertyChangeListener;


public interface PublishExtensionFactory
{
    String ID="publishextension";
	PublishExtension creatExtension(CalendarSelectionModel model, PropertyChangeListener revalidateCallback) throws RaplaException;

    boolean isEnabled();
}