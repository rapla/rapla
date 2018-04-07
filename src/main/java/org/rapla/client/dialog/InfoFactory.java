package org.rapla.client.dialog;

import org.rapla.client.PopupContext;
import org.rapla.framework.RaplaException;

public interface InfoFactory
{
    String getToolTip(Object t);

    String getToolTip(Object obj, boolean wrapHtml);

    /* (non-Javadoc)
     * @see org.rapla.client.swing.gui.view.IInfoUIFactory#showInfoDialog(java.lang.Object, java.awt.ServerComponent, java.awt.Point)
     */
    <T> void showInfoDialog(T object, PopupContext popupContext)
            throws RaplaException;
}
