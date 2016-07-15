package org.rapla.client.gwt;

import java.util.Collection;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import org.gwtbootstrap3.client.ui.constants.DeviceSize;
import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.client.CalendarPlaceView;
import org.rapla.client.RaplaWidget;
import org.rapla.client.gwt.components.TreeComponent;
import org.rapla.client.gwt.components.TreeComponent.SelectionChangeHandler;
import org.rapla.client.gwt.view.NavigatorView;
import org.rapla.client.gwt.view.NavigatorView.NavigatorAction;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Allocatable;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;

@DefaultImplementation(of = CalendarPlaceView.class, context = InjectionContext.gwt)
public class CalendarPlaceViewGwt implements CalendarPlaceView<IsWidget>, NavigatorAction
{
    private static final int OFFSET_NAVIGATION = 50;
    private final TreeComponent treeComponent;
    private final Div completeView = new Div()
    {
        protected void onAttach()
        {
            super.onAttach();
            updateHeights(Window.getClientHeight());
        }
    };
    private final Div drawingContent = new Div();
    private final Div calendarSelection = new Div();
    private final NavigatorView navigatorView;
    private org.rapla.client.CalendarPlaceView.Presenter presenter;

    @Inject
    public CalendarPlaceViewGwt(BundleManager bundleManager)
    {
        Window.addResizeHandler(new ResizeHandler()
        {
            @Override
            public void onResize(ResizeEvent event)
            {
                final int height = event.getHeight();
                updateHeights(height);
            }

        });
        completeView.addStyleName("calendarPlace");
        navigatorView = new NavigatorView(this, bundleManager);
        final Locale locale = bundleManager.getLocale();
        // left side resources navigation whenever medium is medium or large size
        {
            final Div resourcesDiv = new Div();
            resourcesDiv.setVisibleOn(DeviceSize.MD_LG);
            resourcesDiv.addStyleName("resources");
            completeView.add(resourcesDiv);
            treeComponent = new TreeComponent(locale, new SelectionChangeHandler()
            {
                @Override
                public void selectionChanged(Collection<Allocatable> selected)
                {
              //      getPresenter().resourcesSelected(selected);
                }
            });
            resourcesDiv.add(treeComponent);
        }
        final Div containerDiv = new Div();
        containerDiv.addStyleName("drawingContainer");
        completeView.add(containerDiv);
        // header navigation
        {
            final Div headerDiv = new Div();
            containerDiv.add(headerDiv);
            {// calendar selection
                headerDiv.add(calendarSelection);
                calendarSelection.add(new HTML("calendar drop down"));
            }
        }
        containerDiv.add(drawingContent);
    }
    
    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }
    
    protected Presenter getPresenter() {
        return presenter;
    }

    private void updateHeights(final int height)
    {
        final int drawingContentHeight = drawingContent.getParent().getOffsetHeight();
        final int windowHeigth = height - OFFSET_NAVIGATION;
        final int newElementHeight = Math.max(0, Math.max(drawingContentHeight, windowHeigth));
        treeComponent.getElement().getStyle().setHeight(newElementHeight, Unit.PX);
    }
/*
    @Override
    public void updateDate(final Date selectedDate)
    {
        navigatorView.setDate(selectedDate);
    }

    @Override
    public void updateResources(Allocatable[] entries, Collection<Allocatable> selected)
    {
        this.treeComponent.updateData(entries, selected);
    }

    @Override
    public void show(final List<String> viewNames, final String selectedView, final List<String> calendarNames, final String selectedCalendar)
    {
        calendarSelection.clear();
        // Calendars
        Collection<DropDownItem> calendarEntries = new ArrayList<DropDownItem>();
        int i = 0;
        for (String calendarName : calendarNames)
        {
            boolean selected = calendarName.equals(selectedCalendar);
            final DropDownItem item = new DropDownItem(calendarName, i + "", selected);
            i++;
            calendarEntries.add(item);
        }
        final DropDownInputField calendarDropDown = new DropDownInputField("calendar", new DropDownValueChanged()
        {
            @Override
            public void valueChanged(String newValue)
            {
                getPresenter().changeCalendar(calendarNames.get(Integer.parseInt(newValue)));
            }
        }, calendarEntries);
        calendarSelection.add(calendarDropDown);
        // Views
        Collection<DropDownItem> viewEntries = new ArrayList<DropDownItem>();
        i = 0;
        for (String viewName : viewNames)
        {
            boolean selected = viewName.equals(selectedView);
            final DropDownItem item = new DropDownItem(viewName, i + "", selected);
            i++;
            viewEntries.add(item);
        }
        final DropDownInputField viewDropDown = new DropDownInputField("view", new DropDownValueChanged()
        {
            @Override
            public void valueChanged(String newValue)
            {
                getPresenter().changeView(viewNames.get(Integer.parseInt(newValue)));
            }
        }, viewEntries);
        calendarSelection.add(viewDropDown);
        calendarSelection.add(navigatorView);
    }

    @Override
    public void replaceContent(IsWidget contentProvider)
    {
        drawingContent.clear();
        drawingContent.add(contentProvider);
        updateHeights(Window.getClientHeight());
    }

    @Override
    public IsWidget provideContent()
    {
        return completeView;
    }
*/
    @Override
    public void selectedDate(Date selectedDate)
    {
//        getPresenter().selectDate(selectedDate);
    }

    @Override
    public void next()
    {
       // getPresenter().next();
    }

    @Override
    public void previous()
    {
    //    getPresenter().previous();
    }

    @Override public void addSavedViews(RaplaWidget<IsWidget> savedViews)
    {

    }

    @Override public void addSummaryView(RaplaWidget<IsWidget> summaryView)
    {

    }

    @Override public void addConflictsView(RaplaWidget<IsWidget> conflictsView)
    {

    }

    @Override public void addCalendarView(RaplaWidget<IsWidget> calendarView)
    {

    }

    @Override public void addResourceSelectionView(RaplaWidget<IsWidget> resourceSelectionView)
    {

    }

    @Override public void updateView(boolean showConflicts, boolean showSelection, boolean templateMode)
    {

    }

    @Override public IsWidget getComponent()
    {
        return null;
    }
}
