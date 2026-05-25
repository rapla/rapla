package org.rapla.plugin.externaleventimport.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.externaleventimport.ExternalEventImportMetadata;
import org.rapla.plugin.externaleventimport.ExternalEventImportResult;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportDialog;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportResources;
import org.rapla.plugin.externaleventimport.client.ExternalEventImportSubmitCallback;
import org.rapla.scheduler.Promise;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

@Service
public class ExternalEventImportDialogImpl implements ExternalEventImportDialog
{
    private final ClientFacade clientFacade;
    private final RaplaLocale raplaLocale;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final ExternalEventImportResources resources;
    private final RaplaResources raplaResources;
    private final Supplier<ExternalEventImportAllocatableSelectionDialog> allocatableDialogSupplier;

    @Autowired
    public ExternalEventImportDialogImpl(ClientFacade clientFacade, RaplaLocale raplaLocale, DialogUiFactoryInterface dialogUiFactory,
            ExternalEventImportResources resources, RaplaResources raplaResources, Supplier<ExternalEventImportAllocatableSelectionDialog> allocatableDialogSupplier)
    {
        this.clientFacade = clientFacade;
        this.raplaLocale = raplaLocale;
        this.dialogUiFactory = dialogUiFactory;
        this.resources = resources;
        this.raplaResources = raplaResources;
        this.allocatableDialogSupplier = allocatableDialogSupplier;
    }

    @Override
    public Promise<Collection<Allocatable>> showAllocatableSelection(ExternalEventImportMetadata metadata, Collection<Allocatable> available, Collection<Allocatable> preselected)
    {
        return allocatableDialogSupplier.get().show(metadata, available, preselected);
    }

    @Override
    public DialogInterface createImportDialog(PopupContext popupContext, CalendarModel model, ExternalEventImportMetadata metadata,
            ExternalEventImportResult result, ExternalEventImportSubmitCallback callback, boolean closeAfterSubmit)
    {
        String[] options;
        List<DialogInterface.DialogAction> validateActions = new ArrayList<>();
        ExternalEventImportSubmitCallback innerCallback;
        if (closeAfterSubmit)
        {
            options = new String[] { resources.getString("synchronize"), raplaResources.getString("abort") };
            innerCallback = new ExternalEventImportSubmitCallback()
            {
                @Override
                public void submit(List<String> sourceItemIds)
                {
                    callback.submit(sourceItemIds);
                }

                @Override
                public boolean isValidSelection(List<String> sourceItemIds)
                {
                    boolean valid = callback.isValidSelection(sourceItemIds);
                    for (DialogInterface.DialogAction a : validateActions) a.setEnabled(valid);
                    return valid;
                }
            };
        }
        else
        {
            innerCallback = callback;
            options = new String[] { raplaResources.getString("close") };
        }

        ExternalEventImportPanel panel = new ExternalEventImportPanel(clientFacade, raplaResources, raplaLocale, model, dialogUiFactory, resources,
                metadata, result, innerCallback, closeAfterSubmit);
        panel.getComponent().setSize(900, 450);

        DialogInterface di = dialogUiFactory.createContentDialog(popupContext, panel.getComponent(), options);
        String title = MessageFormat.format(resources.getString("import.title"),
                metadata.getSourceName() == null ? "" : metadata.getSourceName());
        di.setTitle(title);

        if (closeAfterSubmit)
        {
            DialogInterface.DialogAction syncAction = di.getAction(0);
            syncAction.setEnabled(false);
            validateActions.add(syncAction);
            syncAction.setRunnable(() -> {
                List<String> picked = panel.getSelectedSourceItemIds();
                if (callback.isValidSelection(picked))
                {
                    callback.submit(picked);
                    di.close();
                }
            });
        }
        return di;
    }

    @Override
    public void busy()
    {
        dialogUiFactory.busy(raplaResources.getString("load"));
    }

    @Override
    public void idle()
    {
        dialogUiFactory.idle();
    }
}
