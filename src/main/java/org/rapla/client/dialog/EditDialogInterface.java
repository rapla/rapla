package org.rapla.client.dialog;

import java.util.Collection;
import java.util.List;

import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.framework.RaplaException;

public interface EditDialogInterface<T>
{

    List<?> getObjects();

    DialogInterface getDialog();

    void start(Collection<T> editObjects, String title, PopupContext popupContext) throws RaplaException;

}
