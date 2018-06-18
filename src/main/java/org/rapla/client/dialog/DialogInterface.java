package org.rapla.client.dialog;

import org.rapla.components.i18n.I18nIcon;
import org.rapla.scheduler.Promise;


public interface DialogInterface
{
    Promise<Integer> start(boolean pack);
    void busy(String message);
    void idle();
    void setTitle(String createTitle);
    void setIcon(I18nIcon iconKey);
    void close();
    DialogAction getAction(int commandIndex);
    void setAbortAction(Runnable abortAction);
    void setDefault(int commandIndex);

    interface DialogAction {
        void setEnabled(boolean enabled);

        boolean isEnabled();

        void setRunnable(Runnable runnable);

        void setIcon(I18nIcon icon);

        void execute();
    }

}
