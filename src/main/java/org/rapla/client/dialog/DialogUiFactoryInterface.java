package org.rapla.client.dialog;

import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.RaplaConnectException;
import org.rapla.storage.dbrm.WrongRaplaVersionException;

public interface  DialogUiFactoryInterface
{

    DialogInterface createContentDialog(PopupContext popupContext, Object content, String[] options);

    DialogInterface createTextDialog(PopupContext popupContext, String title, String text, String[] options);

    DialogInterface createInfoDialog(PopupContext popupContext, String title, String text);

    /** Creates a new ErrorDialog with the specified owner and displays the exception
    @param ex the exception that should be displayed.
    */
    Void showException(Throwable ex, PopupContext popupContext);

    /** Creates a new ErrorDialog with the specified owner and displays the waring */
    Void showWarning(String warning, PopupContext popupContext);
    
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

    PopupContext createPopupContext(RaplaWidget widget);

    void busy(String message);
    void idle();

}