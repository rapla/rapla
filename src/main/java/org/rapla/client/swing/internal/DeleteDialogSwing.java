package org.rapla.client.swing.internal;

import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DeleteDialogInterface;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.swing.InfoFactory;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;

@DefaultImplementation(of=DeleteDialogInterface.class,context = InjectionContext.swing)
public class DeleteDialogSwing implements DeleteDialogInterface {
    private final InfoFactory infoFactory;

    @Inject
    public DeleteDialogSwing(InfoFactory infoFactory) {
        this.infoFactory = infoFactory;
    }

    @Override
    public Promise<Boolean> showDeleteDialog(PopupContext context, Object[] deletables) {
        DialogInterface dlg = null;
        try {
            dlg = infoFactory.createDeleteDialog(deletables, context);
        } catch (RaplaException ex) {
            return new ResolvedPromise<>(ex);
        }
        return dlg.start(true).thenApply(result->result== 0 ? Boolean.TRUE : Boolean.FALSE);

    }
}
