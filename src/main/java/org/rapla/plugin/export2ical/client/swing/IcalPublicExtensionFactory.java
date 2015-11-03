package org.rapla.plugin.export2ical.client.swing;

import java.beans.PropertyChangeListener;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.client.swing.PublishExtension;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.inject.Extension;

import javax.inject.Inject;

@Extension(provides=PublishExtensionFactory.class,id="ical")
public class IcalPublicExtensionFactory extends RaplaComponent implements PublishExtensionFactory
{
	private final RaplaImages raplaImages;

    @Inject
	public IcalPublicExtensionFactory(RaplaContext context, RaplaImages raplaImages)
	{
		super(context);
        this.raplaImages = raplaImages;
	}

	public PublishExtension creatExtension(CalendarSelectionModel model,
			PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new IcalPublishExtension(getContext(), model, raplaImages);
	}

	
}