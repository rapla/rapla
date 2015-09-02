package org.rapla.client;

public interface ApplicationView<W>
{

    interface Presenter
    {
        void menuClicked(String action);
    }

    void setPresenter(Presenter presenter);

    void setLoggedInUser(String loggedInUser);

    void updateMenu();

    void updateContent(W w);

}
