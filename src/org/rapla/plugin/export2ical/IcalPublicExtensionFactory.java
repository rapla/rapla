package org.rapla.plugin.export2ical;

import java.beans.PropertyChangeListener;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.PublishExtension;
import org.rapla.gui.PublishExtensionFactory;

public class IcalPublicExtensionFactory extends RaplaComponent implements PublishExtensionFactory
{
	public IcalPublicExtensionFactory(RaplaContext context)
	{
		super(context);
	}

	public PublishExtension creatExtension(CalendarSelectionModel model,
			PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new IcalPublishExtension(getContext(), model);
	}

	
}