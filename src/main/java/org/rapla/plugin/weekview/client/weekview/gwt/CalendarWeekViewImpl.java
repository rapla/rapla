package org.rapla.plugin.weekview.client.weekview.gwt;

import java.util.List;

import javax.inject.Inject;

import org.rapla.client.PopupContext;
import org.rapla.client.gwt.view.WeekviewGWT;
import org.rapla.client.gwt.view.WeekviewGWT.Callback;
import org.rapla.client.menu.gwt.ContextCreator;
import org.rapla.components.i18n.BundleManager;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.HTMLRaplaBlock;
import org.rapla.plugin.weekview.client.weekview.CalendarWeekView;
import org.rapla.plugin.weekview.client.weekview.HTMLDaySlot;
import org.rapla.plugin.weekview.client.weekview.HTMLWeekViewPresenter.RowSlot;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;

@DefaultImplementation(of = CalendarWeekView.class, context = InjectionContext.gwt)
public class CalendarWeekViewImpl implements CalendarWeekView<IsWidget>, Callback
{

    private final WeekviewGWT calendar;
    Logger logger;
    private org.rapla.plugin.weekview.client.weekview.CalendarWeekView.Presenter presenter;

    @Inject
    public CalendarWeekViewImpl(Logger logger, ContextCreator contextCreator, BundleManager bundleManager)
    {
        calendar = new WeekviewGWT("week", logger, this, contextCreator);
        this.logger = logger;
    }
    
    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }
    
    protected Presenter getPresenter() {
        return presenter;
    }


    @Override
    public IsWidget provideContent()
    {
        FlowPanel container = new FlowPanel();
        container.add(calendar);
        return container;
    }

    @Override
    public void update(final List<HTMLDaySlot> daylist, final List<RowSlot> timelist, final String weeknumber)
    {
        calendar.update(daylist, timelist, weeknumber);
    }

    @Override
    public void updateReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer rowSlot, PopupContext context)
    {
        try
        {
            getPresenter().updateReservation(block, daySlot, rowSlot, context);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void selectReservation(HTMLRaplaBlock block, PopupContext context)
    {
        getPresenter().selectReservation(block, context);
    }

    @Override
    public void newReservation(HTMLDaySlot daySlot, Integer fromMinuteOfDay, Integer tillMinuteOfDay, final PopupContext context)
    {
        try
        {
            getPresenter().newReservation(daySlot, fromMinuteOfDay, tillMinuteOfDay, context);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void resizeReservation(HTMLRaplaBlock block, HTMLDaySlot daySlot, Integer minuteOfDay, PopupContext context)
    {
        try
        {
            getPresenter().resizeReservation(block, daySlot, minuteOfDay, context);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }
}
