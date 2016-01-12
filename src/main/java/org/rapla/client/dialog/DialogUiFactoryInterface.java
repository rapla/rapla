package org.rapla.client.dialog;

import org.rapla.client.PopupContext;
import org.rapla.framework.RaplaException;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.RaplaConnectException;
import org.rapla.storage.dbrm.WrongRaplaVersionException;

public interface DialogUiFactoryInterface
{

    DialogInterface create(PopupContext popupContext, boolean modal, Object content, String[] options) throws RaplaException;

    DialogInterface create(PopupContext popupContext, boolean modal, String title, String text, String[] options) throws RaplaException;

    DialogInterface create(PopupContext popupContext, boolean modal, String title, String text) throws RaplaException;

    /** Creates a new ErrorDialog with the specified owner and displays the exception
    @param ex the exception that should be displayed.
    */
    void showException(Throwable ex, PopupContext popupContext);

    void showError(Exception ex, PopupContext context);

    /** Creates a new ErrorDialog with the specified owner and displays the waring */
    void showWarning(String warning, PopupContext popupContext);

    class Util
    {
        static public boolean isWarningOnly(Throwable ex)
        {
            return ex instanceof RaplaNewVersionException || ex instanceof RaplaSecurityException || ex instanceof WrongRaplaVersionException
                    || ex instanceof RaplaConnectException;
        }

        public static String[] getDefaultOptions()
        {
            return new String[] { "OK" };
        }
    }
}