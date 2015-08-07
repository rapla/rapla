package org.rapla.client;

import java.util.List;

import org.rapla.client.base.CalendarPlugin;

public interface ApplicationView<W>
{

    interface Presenter
    {
        void setSelectedViewIndex(int index);

        void addClicked();

        void changeCalendar(String selectedValue);
    }

    void show(List<String> viewNames, List<String> calendarNames);

    void replaceContent(CalendarPlugin<W> provider);

    void setPresenter(Presenter presenter);

}
