package org.rapla.plugin.autoexport.client.swing;

import java.beans.PropertyChangeListener;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.PublishExtension;
import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.PublishExtensionFactory;
import org.rapla.inject.Extension;
import org.rapla.plugin.autoexport.AutoExportResources;

import javax.inject.Inject;

@Extension(provides=PublishExtensionFactory.class,id="html")
public class HTMLPublicExtensionFactory extends RaplaComponent implements PublishExtensionFactory
{
	private final RaplaResources i18n;
    private final AutoExportResources autoExportI18n;

    @Inject
	public HTMLPublicExtensionFactory(RaplaContext context, AutoExportResources autoExportI18n, RaplaResources i18n) {
		super(context);
		this.autoExportI18n = autoExportI18n;
		this.i18n = i18n;
	}

	public PublishExtension creatExtension(CalendarSelectionModel model,PropertyChangeListener revalidateCallback) throws RaplaException 
	{
		return new HTMLPublishExtension(getContext(), model, autoExportI18n, i18n);
	}

}