package org.rapla.client;

import org.rapla.client.event.ApplicationEvent;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Observable;

import java.util.function.Function;

public interface ApplicationView<T>
{
    void close();

    PopupContext createPopupContext();

    void removeWindow(ApplicationEvent windowId);
    boolean hasWindow(ApplicationEvent windowId);
    void openWindow(ApplicationEvent windowId, PopupContext popupContext, RaplaWidget<T> component, String title, Function<ApplicationEvent, Boolean> windowClosing, Observable<String> busyIdleObservable);
    void requestFocus(ApplicationEvent windowId);

    interface Presenter
    {
        void menuClicked(String action);

        boolean mainClosing();
    }

    void setPresenter(Presenter presenter);

    void setStatusMessage(String message, boolean hightlight);

    void init(boolean showToolTips, String windowTitle);

    void updateView( ModificationEvent event) throws RaplaException;

    void updateMenu();

    void updateContent(RaplaWidget<T> w);


}
