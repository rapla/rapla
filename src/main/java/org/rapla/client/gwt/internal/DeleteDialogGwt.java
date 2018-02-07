package org.rapla.client.gwt.internal;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DeleteDialogInterface;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;

@DefaultImplementation(of=DeleteDialogInterface.class,context = InjectionContext.gwt)
public class DeleteDialogGwt implements DeleteDialogInterface {

    final private DialogUiFactoryInterface dialogUiFactory;
    final private RaplaResources i18n;
    @Inject
    public DeleteDialogGwt(DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n) {
        this.dialogUiFactory = dialogUiFactory;
        this.i18n = i18n;
    }

    @Override
    public Promise<Boolean> showDeleteDialog(PopupContext context, Object[] deletables) {
        DialogInterface dlg = null;
        try {
            String[] options = new String[] {i18n.getString("delete"), i18n.getString("abort")};
            String title = i18n.getString("delete");
            StringBuilder deletablesText = new StringBuilder();
            boolean first = true;
            for (Object deletable: deletables)
            {
                if (first)
                {
                    first = false;
                    deletablesText.append(", ");
                }
                deletablesText.append(deletable);
            }
            dlg = dialogUiFactory.create(context,false,title,deletablesText.toString(), options);
        } catch (RaplaException ex) {
            return new ResolvedPromise<>(ex);
        }
        return dlg.start(true).thenApply(result->result== 0 ? Boolean.TRUE : Boolean.FALSE);

    }
}
