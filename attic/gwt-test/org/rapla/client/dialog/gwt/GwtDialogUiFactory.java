package org.rapla.client.dialog.gwt;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.gwt.components.VueComponent;
import org.rapla.client.dialog.gwt.components.VueLabel;
import org.rapla.client.gwt.VuePopupContext;
import org.rapla.entities.DependencyException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.storage.dbrm.RaplaConnectException;
import org.rapla.storage.dbrm.RaplaRestartingException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Iterator;

@Singleton
@DefaultImplementation(context = InjectionContext.gwt, of = DialogUiFactoryInterface.class)
public class GwtDialogUiFactory implements DialogUiFactoryInterface
{

    private final RaplaResources i18n;
    private final Logger logger;

    @Inject
    public GwtDialogUiFactory(RaplaResources i18n, Logger logger)
    {
        this.i18n = i18n;
        this.logger = logger;
    }

    @Override
    public VueDialog createContentDialog(PopupContext popupContext, Object content, String[] options)
    {
        logger.info("createContentDialog with content " + content.getClass());
        return new VueDialog((VueComponent) content, options);
    }

    @Override
    public VueDialog createTextDialog(PopupContext popupContext, String title, String text, String[] options)
    {
        logger.info("createTextDialog with text " + text);
        final VueDialog di = createContentDialog(popupContext, new VueLabel(text), options);
        di.setTitle(title);
        return di;
    }

    @Override
    public VueDialog createInfoDialog(PopupContext popupContext, String title, String text)
    {
        final VueDialog di = createTextDialog(popupContext, title, text, DialogUiFactoryInterface.Util.getDefaultOptions());
        di.setTitle(title);
        return di;
    }

    @Override
    public Void showException(Throwable ex, PopupContext popupContext)
    {
        if (ex instanceof RaplaConnectException)
        {
            String message = ex.getMessage();
            Throwable cause = ex.getCause();
            String additionalInfo = "";
            if (cause != null)
            {
                additionalInfo = " " + cause.getClass() + ":" + cause.getMessage();
            }

            logger.warn(message + additionalInfo);
            if (ex instanceof RaplaRestartingException)
            {
                return Promise.VOID;
            }
            try
            {
                final String key = "error";
                final String title = i18n.format("exclamation.format", i18n.getString(key));
                final DialogInterface di = createInfoDialog(popupContext, title, message);
                di.start(true);
            }
            catch (Throwable e)
            {
                logger.error(e.getMessage(), e);
            }
        }
        else
        {
            try
            {
                final String message;
                if (ex instanceof DependencyException)
                {
                    message = getHTML((DependencyException) ex);
                }
                else if (DialogUiFactoryInterface.Util.isWarningOnly(ex))
                {
                    message = ex.getMessage();
                }
                else
                {
                    message = stacktrace(ex);
                }
                final String key = "error";
                final String title = i18n.format("exclamation.format", i18n.getString(key));
                DialogInterface di = createInfoDialog(popupContext, title, message);
                di.start(true);
            }
            catch (Throwable ex2)
            {
                logger.error(ex2.getMessage(), ex2);
            }
        }
        return Promise.VOID;
    }

    private String stacktrace(Throwable ex)
    {
        StringBuilder sb = new StringBuilder();
        final StackTraceElement[] stackTrace = ex.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace)
        {
            sb.append(stackTraceElement.getFileName());
            sb.append(".");
            sb.append(stackTraceElement.getMethodName());
        }
        return sb.toString();
    }

    static private String getHTML(DependencyException ex)
    {
        StringBuffer buf = new StringBuffer();
        buf.append(ex.getMessageText() + ":");
        buf.append("<br><br>");
        Iterator<String> it = ex.getDependencies().iterator();
        int i = 0;
        while (it.hasNext())
        {
            Object obj = it.next();
            buf.append((++i));
            buf.append(") ");

            buf.append(obj);

            buf.append("<br>");
            if (i == 30 && it.hasNext())
            {
                buf.append("... " + (ex.getDependencies().size() - 30) + " more");
                break;
            }
        }
        return buf.toString();
    }

    @Override
    public Void showWarning(String warning, PopupContext popupContext)
    {
    	return Promise.VOID;
    }

    @Override public PopupContext createPopupContext(RaplaWidget widget)
    {
//        final Object component = widget.getComponent();
        // todo maybe add component here
        // TODO: use vue dialog
        return new VuePopupContext();
    }

    @Override
    public void busy(String message) {

    }

    @Override
    public void idle() {

    }

}
