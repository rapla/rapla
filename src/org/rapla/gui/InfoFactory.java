package org.rapla.gui;

import java.awt.Component;
import java.awt.Point;

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

    <T> void showInfoDialog( T object, Component owner ) throws RaplaException;

    <T> void showInfoDialog( T object, Component owner, Point point ) throws RaplaException;

    DialogUI createDeleteDialog( Object[] deletables, Component owner ) throws RaplaException;

}