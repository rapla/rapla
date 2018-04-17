package org.rapla.client.internal;

import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClassifiableFilter;

import java.util.LinkedHashMap;

public interface MultiCalendarView extends RaplaWidget
{

    interface Presenter
    {
        void onViewSelectionChange(PopupContext context);
        
        void onFilterChange();
    }

    void setPresenter(Presenter p);

    void setFilterModel(final ClassifiableFilter model);
    
    String getSelectedViewId();

    void setCalendarView(SwingCalendarView calendarView);

    void setSelectableViews(LinkedHashMap<String, SwingViewFactory> viewIdToViewName);

    ClassificationFilter[] getFilters();

    void setSelectedViewId(String viewId);

    void closeFilterButton();

    void setNoViewText(String errorNoViewDefined);
}
