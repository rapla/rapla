package org.rapla.client.dialog;

import org.rapla.client.PopupContext;
import org.rapla.scheduler.Promise;

public interface DeleteDialogInterface {

    Promise<Boolean> showDeleteDialog(PopupContext context, Object[] deletables);
}
