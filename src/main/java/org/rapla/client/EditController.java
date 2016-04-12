package org.rapla.client;

import java.util.List;

import org.eclipse.jetty.util.Promise;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;

public interface EditController
{
    <T extends Entity> void edit( T obj, PopupContext popupContext ) throws RaplaException;
//  neue Methoden zur Bearbeitung von mehreren gleichartigen Elementen (Entities-Array)
//  orientieren sich an den oberen beiden Methoden zur Bearbeitung von einem Element
    <T extends Entity> void edit( List<T> obj, PopupContext popupContext ) throws RaplaException;
}