package org.rapla.gui;

import javax.swing.JComponent;

import org.rapla.framework.RaplaException;
import org.rapla.gui.toolkit.DialogUI;

public interface InfoFactory
{
    <T> JComponent createInfoComponent( T object ) throws RaplaException;

    /** same as getToolTip(obj, true) */
    <T> String getToolTip( T obj );

    /** @param wrapHtml wraps an html Page arround the tooltip */
    <T> String getToolTip( T obj, boolean wrapHtml );

    <T> void showInfoDialog( T object, PopupContext popupContext ) throws RaplaException;

    DialogUI createDeleteDialog( Object[] deletables, PopupContext popupContext ) throws RaplaException;

}