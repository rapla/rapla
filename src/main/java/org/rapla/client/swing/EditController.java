package org.rapla.client.swing;

import org.rapla.client.PopupContext;
import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;

import java.util.List;

public interface EditController
{
    <T extends Entity> void edit( T obj, PopupContext popupContext ) throws RaplaException;
    <T extends Entity> void editNew( T obj, PopupContext popupContext ) throws RaplaException;
    <T extends Entity> void edit( T obj, String title, PopupContext popupContext, EditCallback<T> callback) throws RaplaException;
//  neue Methoden zur Bearbeitung von mehreren gleichartigen Elementen (Entities-Array)
//  orientieren sich an den oberen beiden Methoden zur Bearbeitung von einem Element
    <T extends Entity> void edit( List<T> obj, String title,PopupContext popupContext,EditCallback<List<T>> callback ) throws RaplaException;

    public interface EditCallback<T>
    {
        void onFailure(Throwable e);
        void onSuccess(T editObject);
        void onAbort();
    }

}