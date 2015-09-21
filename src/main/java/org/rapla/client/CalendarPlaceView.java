package org.rapla.client;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.rapla.entities.domain.Allocatable;

public interface CalendarPlaceView<W>
{

    public interface Presenter
    {
        void changeView(String view);

        void changeCalendar(String selectedValue);

        void resourcesSelected(Collection<Allocatable> selected);

        void selectDate(Date newDate);

        void next();

        void previous();
    }

    void show(List<String> viewNames, String selectedView, List<String> calendarNames, String selectedCalendar);

    void replaceContent(W provider);

    void setPresenter(Presenter presenter);

    void updateResources(Allocatable[] entries, Collection<Allocatable> selected);

    W provideContent();

    void updateDate(Date selectedDate);

}
