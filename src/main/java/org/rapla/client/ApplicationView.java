package org.rapla.client;

import org.rapla.client.swing.toolkit.RaplaWidget;

public interface ApplicationView<T>
{
    interface Presenter
    {
        void menuClicked(String action);

        void mainClosing();
    }

    void setPresenter(Presenter presenter);

    void setStatusMessage(String message, boolean hightlight);

    void init(boolean showToolTips, String windowTitle);

    void updateMenu();

    void updateContent(RaplaWidget<T> w);

    void createPopup(RaplaWidget<T> w);


}
