package org.rapla.client;

public interface ApplicationView<W>
{

    interface Presenter
    {
        // TODO Menu click...
    }

    void setPresenter(Presenter presenter);

    void setLoggedInUser(String loggedInUser);

    void updateMenu();

    void updateContent(W w);

}
