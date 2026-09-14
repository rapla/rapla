package org.rapla.plugin.externaleventimport.client;

import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.plugin.externaleventimport.ExternalEventImportMetadata;
import org.rapla.plugin.externaleventimport.ExternalEventImportResult;
import org.rapla.scheduler.Promise;

import java.util.Collection;

public interface ExternalEventImportDialog
{
    /**
     * Shows a generic allocatable-selection tree, scoped to the rapla
     * {@code DynamicType} key the server's metadata declares as importable.
     */
    Promise<Collection<Allocatable>> showAllocatableSelection(ExternalEventImportMetadata metadata, Collection<Allocatable> available, Collection<Allocatable> preselected);

    DialogInterface createImportDialog(PopupContext popupContext, CalendarModel model, ExternalEventImportMetadata metadata, ExternalEventImportResult result,
            ExternalEventImportSubmitCallback callback, boolean closeAfterSubmit);

    /** Blocks the window the action originated from (per {@code popupContext}) while loading —
     *  e.g. the reservation edit window for the sync flow, the main window for the import wizard. */
    void busy(PopupContext popupContext);

    void idle(PopupContext popupContext);
}
