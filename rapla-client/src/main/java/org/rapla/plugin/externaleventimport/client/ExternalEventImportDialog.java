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

    void busy();

    void idle();
}
