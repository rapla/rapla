package org.rapla.plugin.externaleventimport.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.extensionpoints.ReservationWizardExtension;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.externaleventimport.ExternalEventImportPlugin;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportController;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportEnabledCondition;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportResources;
import org.rapla.plugin.tempatewizard.client.TemplateWizard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

@Service(ExternalEventImportPlugin.PLUGIN_ID)
@Conditional(ExternalEventImportEnabledCondition.class)
public class ExternalEventImportWizard extends TemplateWizard implements IdentifiableMenuEntry, ReservationWizardExtension
{
    private final ExternalEventImportResources resources;
    private final ExternalEventImportController importController;
    /** Source name (e.g. "Dualis") for the menu label's {@code {0}} placeholder.
     *  Populated async at construction time from {@link ExternalEventImportController#getMetadata()};
     *  defaults to empty so the menu still renders if the call hasn't finished
     *  by the time the user opens the menu. The controller caches the metadata,
     *  so a brief lag on the very first menu open is the worst case. */
    private volatile String sourceName = "";

    @Autowired
    public ExternalEventImportWizard(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model,
            DialogUiFactoryInterface dialogUiFactory, ExternalEventImportResources resources, ExternalEventImportController importController,
            ApplicationEventBus eventBus, MenuItemFactory menuItemFactory, org.rapla.rest.PluginsService plugins) throws RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger, model, eventBus, dialogUiFactory, menuItemFactory, plugins);
        this.resources = resources;
        this.importController = importController;
        importController.getMetadata()
                .thenAccept(m -> sourceName = m.getSourceName() == null ? "" : m.getSourceName())
                .exceptionally(ex -> logger.warn("Could not fetch external-event-import metadata: " + ex.getMessage()));
    }

    @Override
    public String getId()
    {
        return "300_externaleventimportWizard";
    }

    @Override
    protected String getMultipleTemplateName()
    {
        return resources.getString("import.menu.entry").replace("{0}", sourceName);
    }

    @Override
    protected String getSingleTemplateName(String templateName)
    {
        return getMultipleTemplateName() + " " + templateName;
    }

    @Override
    protected void createWithTemplate(Allocatable template)
    {
        PopupContext popupContext = dialogUiFactory.createPopupContext(null);
        importController.importEvents(popupContext, template);
    }

    @Override
    public boolean isEnabled()
    {
        return true;
    }
}
