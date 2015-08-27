package org.rapla.client;

import java.util.Collection;
import java.util.List;

import org.rapla.client.base.CalendarPlugin;
import org.rapla.entities.domain.Allocatable;

public interface ApplicationView<W>
{

    interface Presenter
    {
        void setSelectedViewIndex(int index);

        void addClicked();

        void changeCalendar(String selectedValue);

        void resourcesSelected(Collection<Allocatable> selected);
    }

    void show(List<String> viewNames, List<String> calendarNames);

    void replaceContent(CalendarPlugin<W> provider);

    void setPresenter(Presenter presenter);

    void update(Allocatable[] entries, Collection<Allocatable> selected);

}
