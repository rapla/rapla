package org.rapla.client.gwt.internal;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DeleteDialogInterface;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.entities.Named;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;

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
            if ( deletable instanceof Named)
            {
                deletablesText.append(((Named)deletable).getName(i18n.getLocale()));
            }
            else
            {
                deletablesText.append(deletable);
            }
        }
        dlg = dialogUiFactory.createTextDialog(context, title,deletablesText.toString(), options);
        return dlg.start(true).thenApply(result->result== 0 ? Boolean.TRUE : Boolean.FALSE);

    }
}
