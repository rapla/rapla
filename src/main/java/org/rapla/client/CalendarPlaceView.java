package org.rapla.client;

import java.util.Collection;
import java.util.List;

import org.rapla.client.base.CalendarPlugin;
import org.rapla.entities.domain.Allocatable;

public interface CalendarPlaceView<W>
{

    public interface Presenter
    {
        void changeView(String view);

        void changeCalendar(String selectedValue);

        void resourcesSelected(Collection<Allocatable> selected);
    }

    void show(List<String> viewNames, String selectedView, List<String> calendarNames, String selectedCalendar);

    void replaceContent(CalendarPlugin<W> provider);

    void setPresenter(Presenter presenter);

    void updateResources(Allocatable[] entries, Collection<Allocatable> selected);

    W provideContent();

}
