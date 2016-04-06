package org.rapla.client;

import org.eclipse.jetty.util.Promise;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;

import java.util.List;

public interface EditController
{
    <T extends Entity> void edit( T obj, PopupContext popupContext ) throws RaplaException;
    <T extends Entity> Promise<T> edit( T obj, String title, PopupContext popupContext) throws RaplaException;
//  neue Methoden zur Bearbeitung von mehreren gleichartigen Elementen (Entities-Array)
//  orientieren sich an den oberen beiden Methoden zur Bearbeitung von einem Element
    <T extends Entity>RaplaWidget edit( List<T> obj, String title,PopupContext popupContext,EditCallback<List<T>> callback ) throws RaplaException;

    interface EditCallback<T>
    {
        void onFailure(Throwable e);
        void onSuccess(T editObject);
        void onAbort();
    }

}