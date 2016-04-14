package org.rapla.client;

import java.awt.Component;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.entities.domain.Allocatable;

public interface CalendarPlaceView<W> extends  RaplaWidget<W>
{
    interface Presenter
    {
        void minmaxPressed();

        void closeTemplate();
    }

    void addSavedViews(RaplaWidget<W> savedViews);
    void addSummaryView(RaplaWidget<W> summaryView);

    void addConflictsView(RaplaWidget<W> conflictsView);

    void addCalendarView(RaplaWidget<W> calendarView);

    void addResourceSelectionView(RaplaWidget<W> resourceSelectionView);

    void setPresenter(Presenter presenter);

    void updateView(boolean showConflicts, boolean showSelection, boolean templateMode);

}
