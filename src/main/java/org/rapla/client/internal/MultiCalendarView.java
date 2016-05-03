package org.rapla.client.internal;

import java.util.LinkedHashMap;

import org.rapla.client.PopupContext;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.RaplaWidget;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClassifiableFilter;

public interface MultiCalendarView<T> extends RaplaWidget<T>
{

    public interface Presenter
    {
        void onViewSelectionChange(PopupContext context);
        
        void onFilterChange();
    }

    void setPresenter(Presenter p);

    void setFilterModel(final ClassifiableFilter model);
    
    String getSelectedViewId();

    void setCalendarView(SwingCalendarView<T> calendarView);

    void setSelectableViews(LinkedHashMap<String, String> viewIdToViewName);

    ClassificationFilter[] getFilters();

    void setSelectedViewId(String viewId);

    void closeFilterButton();

    void setNoViewText(String errorNoViewDefined);
}
