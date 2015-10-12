package org.rapla.client.swing;

import org.rapla.client.PopupContext;
import org.rapla.framework.RaplaException;

public interface InfoFactory<C, D>
{
    <T> C createInfoComponent( T object ) throws RaplaException;

    /** same as getToolTip(obj, true) */
    <T> String getToolTip( T obj );

    /** @param wrapHtml wraps an html Page arround the tooltip */
    <T> String getToolTip( T obj, boolean wrapHtml );

    <T> void showInfoDialog( T object, PopupContext popupContext ) throws RaplaException;

    D createDeleteDialog( Object[] deletables, PopupContext popupContext ) throws RaplaException;

}