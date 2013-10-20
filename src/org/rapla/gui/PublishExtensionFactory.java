package org.rapla.gui;

import java.beans.PropertyChangeListener;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaException;

public interface PublishExtensionFactory
{
	PublishExtension creatExtension(CalendarSelectionModel model, PropertyChangeListener revalidateCallback) throws RaplaException;
}