package org.rapla.plugin.autoexport;

import java.beans.PropertyChangeListener;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.PublishExtension;
import org.rapla.gui.PublishExtensionFactory;
import org.rapla.inject.Extension;

@Extension(provides=PublishExtensionFactory.class,id="html")
public class HTMLPublicExtensionFactory extends RaplaComponent implements PublishExtensionFactory
{
	public HTMLPublicExtensionFactory(RaplaContext context) {
		super(context);
	}

	public PublishExtension creatExtension(CalendarSelectionModel model,PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new HTMLPublishExtension(getContext(), model);
	}

}